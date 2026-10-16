package app.materialfrontend

import app.materialfrontend.data.Gamelists
import app.materialfrontend.data.LauncherSources
import app.materialfrontend.data.AppPaths
import app.materialfrontend.data.Ids
import app.materialfrontend.data.LibraryStore
import app.materialfrontend.data.RomScanner
import app.materialfrontend.data.SettingsStore
import app.materialfrontend.data.SteamLibrary
import app.materialfrontend.emulators.CoreInstaller
import app.materialfrontend.emulators.DetectedEmulator
import app.materialfrontend.emulators.EmulatorDetector
import app.materialfrontend.emulators.LaunchTarget
import app.materialfrontend.emulators.Launchers
import app.materialfrontend.input.ControllerManager
import app.materialfrontend.libretro.EmulationSession
import app.materialfrontend.model.Game
import app.materialfrontend.model.GameKind
import app.materialfrontend.model.GameMeta
import app.materialfrontend.model.Platforms
import app.materialfrontend.model.Settings
import app.materialfrontend.model.UiMode
import app.materialfrontend.scraper.ScrapeService
import app.materialfrontend.scraper.createHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExternalRun(val game: Game, val label: String, val process: Process, val startedAt: Long)

data class ScanState(val running: Boolean = false, val current: String = "", val found: Int = 0)

/** Application-wide state & actions (one per process). */
class AppController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = createHttpClient()
    val settings = SettingsStore(scope)
    val library = LibraryStore(scope)
    val controllers = ControllerManager()
    val detector = EmulatorDetector()
    val coreInstaller = CoreInstaller(http)
    val scraper = ScrapeService(http, library, settings, scope)

    val mode = MutableStateFlow(settings.value.defaultMode)
    val fullscreen = MutableStateFlow(settings.value.startFullscreen)

    private val _session = MutableStateFlow<EmulationSession?>(null)
    val session: StateFlow<EmulationSession?> = _session.asStateFlow()
    private var sessionStart = 0L

    private val _external = MutableStateFlow<ExternalRun?>(null)
    val external: StateFlow<ExternalRun?> = _external.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Window minimise requests (external emulator launch). */
    val minimizeRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 2)

    fun toast(msg: String) { toasts.tryEmit(msg) }

    init {
        // Pre-branding default accent → brand purple.
        if (settings.value.seedColor == 0xFF5B5BD6) settings.update { it.copy(seedColor = 0xFF6750A4) }
        controllers.nintendoConfirm = settings.value.nintendoConfirm
        controllers.start()
        scope.launch {
            controllers.connected.collect { info ->
                toast("${info.typeLabel} connected")
                if (settings.value.bigScreenOnController && mode.value == UiMode.LIST && session.value == null) {
                    mode.value = UiMode.BIGSCREEN; fullscreen.value = true
                }
            }
        }
    }

    fun updateSettings(fn: (Settings) -> Settings) {
        settings.update(fn)
        controllers.nintendoConfirm = settings.value.nintendoConfirm
    }

    // ---------- Library ----------

    fun rescan(scrapeAfter: Boolean = true) {
        if (_scan.value.running) return
        scope.launch(Dispatchers.IO) {
            _scan.value = ScanState(true)
            val s = settings.value
            val existing = library.value.associateBy { it.id }
            val roms = RomScanner().scan(s.romFolders, existing) { _scan.value = _scan.value.copy(current = it) }.games
            _scan.value = _scan.value.copy(current = "Steam")
            val steamLib = SteamLibrary()
            val steamGames = if (s.importSteam) runCatching { steamLib.scan() + steamLib.shortcuts() }.getOrDefault(emptyList()) else emptyList()
            val steam = steamGames.map { g ->
                existing[g.id]?.let { prev -> prev.copy(path = g.path, assets = prev.assets.copy(cover = prev.assets.cover ?: g.assets.cover, hero = prev.assets.hero ?: g.assets.hero, logo = prev.assets.logo ?: g.assets.logo)) } ?: g
            }
            _scan.value = _scan.value.copy(current = "Other launchers")
            val launchers = if (s.importLaunchers) runCatching { LauncherSources.scanAll() }.getOrDefault(emptyList()).map { g ->
                existing[g.id]?.let { prev -> prev.copy(path = g.path, assets = prev.assets.copy(cover = prev.assets.cover ?: g.assets.cover, hero = prev.assets.hero ?: g.assets.hero)) } ?: g
            } else emptyList()
            val imported = if (s.importGamelists) Gamelists.apply(roms, s.romFolders) else roms
            val keep = library.value.filter { it.kind == GameKind.PORT }
            val all = (imported + steam + launchers + keep).distinctBy { it.id }
            library.update { all }
            _scan.value = ScanState(false, "", all.size)
            toast("Library updated · ${all.size} games")
            if (scrapeAfter && s.scraper.autoScrapeNew) scraper.scrape(all)
        }
    }

    fun toggleFavorite(id: String) = library.updateGame(id) { it.copy(favorite = !it.favorite) }
    fun setHidden(ids: Set<String>, hidden: Boolean) = library.update { l -> l.map { if (it.id in ids) it.copy(hidden = hidden) else it } }
    fun rename(id: String, title: String) = library.updateGame(id) { it.copy(title = title, titleLocked = true) }
    fun setLauncher(id: String, key: String?) = library.updateGame(id) { it.copy(launcherOverride = key) }

    fun addPort(exe: File, title: String, platformId: String, args: String?): Game {
        val g = Game(
            id = Ids.of("port:" + exe.absolutePath), title = title, platformId = platformId, kind = GameKind.PORT,
            path = exe.absolutePath, launchArgs = args?.takeIf { it.isNotBlank() }, titleLocked = true, meta = GameMeta(),
        )
        library.upsertAll(listOf(g))
        scraper.scrape(listOf(g), force = true)
        return g
    }

    fun removeGames(ids: Set<String>) = library.remove(ids)

    // ---------- Emulators ----------

    suspend fun detectEmulators(onProgress: (String) -> Unit = {}): List<DetectedEmulator> =
        withContext(Dispatchers.IO) { detector.detect(onProgress) }

    // ---------- Launching ----------

    fun play(game: Game, launcherKey: String? = null) {
        if (_session.value != null || _external.value != null) { toast("A game is already running"); return }
        val s = settings.value
        when (val t = Launchers.resolve(game, s, launcherKey)) {
            is LaunchTarget.Core -> {
                val session = EmulationSession(t.file, game.path, game.id, controllers, s.biosDir?.let(::File) ?: AppPaths.system, s.rewind)
                controllers.gameMode = true
                sessionStart = System.currentTimeMillis()
                _session.value = session
                session.start()
                markPlayed(game)
            }
            is LaunchTarget.External -> launchExternal(game, t)
            is LaunchTarget.Steam -> { Launchers.openSteam(t.appId); markPlayed(game); toast("Opening ${game.title} in Steam") }
            is LaunchTarget.Uri -> { Launchers.openUri(t.uri); markPlayed(game); toast("Opening ${game.title} in ${t.label}") }
            is LaunchTarget.Unavailable -> toast(t.reason)
        }
    }

    private fun launchExternal(game: Game, t: LaunchTarget.External) {
        val proc = runCatching {
            ProcessBuilder(t.command).apply { t.workingDir?.let { directory(it) }; redirectErrorStream(true); redirectOutput(ProcessBuilder.Redirect.DISCARD) }.start()
        }.getOrElse { toast("Couldn't start ${t.label}: ${it.message}"); return }
        val run = ExternalRun(game, t.label, proc, System.currentTimeMillis())
        _external.value = run
        markPlayed(game)
        if (settings.value.minimizeOnExternalLaunch) minimizeRequests.tryEmit(true)
        scope.launch(Dispatchers.IO) {
            val code = proc.waitFor()
            val seconds = (System.currentTimeMillis() - run.startedAt) / 1000
            library.updateGame(game.id) { it.copy(playTimeSeconds = it.playTimeSeconds + seconds) }
            _external.value = null
            minimizeRequests.tryEmit(false)
            if (code != 0 && seconds < 5) toast("${t.label} exited immediately (code $code) — check its path and arguments")
        }
    }

    fun stopExternal() { _external.value?.process?.let { p -> p.descendants().forEach { it.destroy() }; p.destroy() } }

    fun stopSession() {
        val s = _session.value ?: return
        if (settings.value.autoSaveState) s.saveState(0)
        s.stop()
        controllers.gameMode = false
        val seconds = (System.currentTimeMillis() - sessionStart) / 1000
        library.updateGame(s.gameId) { it.copy(playTimeSeconds = it.playTimeSeconds + seconds) }
        _session.value = null
    }

    private fun markPlayed(game: Game) = library.updateGame(game.id) { it.copy(lastPlayed = System.currentTimeMillis(), playCount = it.playCount + 1) }

    fun platformName(id: String) = Platforms.get(id).name

    fun shutdown() {
        _session.value?.let { stopSession() }
        settings.save(); library.save()
        controllers.stop()
    }
}
