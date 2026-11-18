package cc.xdan.frontend

import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.CompatSteam
import cc.xdan.frontend.data.Installs
import cc.xdan.frontend.data.InstallTarget
import cc.xdan.frontend.data.SteamAppInfo
import cc.xdan.frontend.data.SteamOwned
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.Usage
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.input.RetroButton
import cc.xdan.frontend.input.InputMaps
import cc.xdan.frontend.ui.settings.SettingsSection
import cc.xdan.frontend.emulators.CoreCatalog
import cc.xdan.frontend.data.Gamelists
import cc.xdan.frontend.data.LauncherSources
import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.data.Ids
import cc.xdan.frontend.data.LibraryStore
import cc.xdan.frontend.data.RomScanner
import cc.xdan.frontend.data.SettingsStore
import cc.xdan.frontend.data.SteamLibrary
import cc.xdan.frontend.emulators.CoreInstaller
import cc.xdan.frontend.emulators.DetectedEmulator
import cc.xdan.frontend.emulators.EmulatorDetector
import cc.xdan.frontend.emulators.LaunchTarget
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.input.ControllerManager
import cc.xdan.frontend.libretro.EmulationSession
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.GameMeta
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.Settings
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.scraper.ScrapeService
import cc.xdan.frontend.scraper.createHttpClient
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

/** How a launch can tell it's finished: core first frame, an external process, or an OS-opened link. */
enum class LaunchKind { CORE, PROCESS, URI }

/** Shown as the "Launching…" screen in Big Screen / XMB until the title is up. [via] = null for native titles. */
data class LaunchingState(val game: Game, val via: String?, val kind: LaunchKind, val at: Long = System.currentTimeMillis())

data class CoreInstallProgress(val total: Int, val done: Int, val current: String?, val fraction: Float) {
    val finished get() = done >= total
}

data class ScanState(val running: Boolean = false, val current: String = "", val found: Int = 0)

/** Application-wide state & actions (one per process). */
class AppController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = createHttpClient()
    val settings = SettingsStore(scope)
    private val _runnable = MutableStateFlow<Set<String>>(emptySet())
    /** Systems that can run right now; ROMs for other systems stay in the library, greyed out. */
    val runnable: StateFlow<Set<String>> = _runnable.asStateFlow()
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

    private val _launching = MutableStateFlow<LaunchingState?>(null)
    val launching: StateFlow<LaunchingState?> = _launching.asStateFlow()

    /** The launch screen is done (title is up or we stopped waiting): now minimise if wanted. */
    fun launchSettled() {
        val l = _launching.value ?: return
        _launching.value = null
        if (l.kind != LaunchKind.CORE && settings.value.minimizeOnExternalLaunch && (l.kind == LaunchKind.URI || _external.value != null)) {
            minimizeRequests.tryEmit(true)
        }
    }

    /** Ask the Library view to open Settings at a section (from Big Screen / XMB "edit in Library view"). */
    val openSettings = MutableStateFlow<SettingsSection?>(null)

    fun editInLibraryView(section: SettingsSection) {
        mode.value = UiMode.LIST; fullscreen.value = false
        openSettings.value = section
    }

    private val _coreInstall = MutableStateFlow<CoreInstallProgress?>(null)
    val coreInstall: StateFlow<CoreInstallProgress?> = _coreInstall.asStateFlow()

    /** Installs cores in the background (keeps going while the user moves on). */
    fun installCores(ids: List<String>) {
        if (ids.isEmpty() || _coreInstall.value?.finished == false) return
        scope.launch(Dispatchers.IO) {
            ids.forEachIndexed { i, id ->
                _coreInstall.value = CoreInstallProgress(ids.size, i, CoreCatalog.get(id)?.name, 0f)
                coreInstaller.install(id) { f -> _coreInstall.value = _coreInstall.value?.copy(fraction = f) }
                    .onFailure { toast("${CoreCatalog.get(id)?.name}: ${it.message}") }
            }
            _coreInstall.value = CoreInstallProgress(ids.size, ids.size, null, 1f)
            refreshRunnable()
        }
    }

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
        Platforms.setCustom(settings.value.customSystems)
        scope.launch { settings.state.collect { Platforms.setCustom(it.customSystems); refreshRunnable(); controllers.padBindings = cc.xdan.frontend.input.UiBindings.padOverrides(it.uiPadBindings) } }
        controllers.start()
        // Pick up games added since last time (new ROMs, newly installed Steam/launcher games).
        // (Tests set mf.noStartupScan so a saved "set up" state doesn't scan the real machine mid-test.)
        if (settings.value.onboardingComplete && settings.value.rescanOnStartup && System.getProperty("mf.noStartupScan") == null) rescan()
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

    fun refreshRunnable() { _runnable.value = runnablePlatforms() }

    /** Something new found on this machine during a rescan, waiting for "add it?". */
    data class Discovered(val key: String, val name: String, val detail: String, val emulator: DetectedEmulator? = null)
    private val _discoveries = MutableStateFlow<List<Discovered>>(emptyList())
    val discoveries: StateFlow<List<Discovered>> = _discoveries.asStateFlow()

    // ---------- Frontend control remapping ----------
    /** Set while waiting for a key to bind (Main's key handler delivers the next key-down here). */
    @Volatile var keyCapture: ((androidx.compose.ui.input.key.Key) -> Unit)? = null
    private val _binding = MutableStateFlow<Nav?>(null)
    /** The action currently waiting for a key or button. */
    val binding: StateFlow<Nav?> = _binding.asStateFlow()

    fun startBinding(nav: Nav, label: String) {
        _binding.value = nav
        toast("Press a key or controller button for “$label” (Esc cancels)")
        keyCapture = { k -> updateSettings { it.copy(uiKeyBindings = cc.xdan.frontend.input.UiBindings.withKey(it.uiKeyBindings, nav, k.keyCode)) }; endBinding("$label → ${cc.xdan.frontend.input.UiBindings.keyName(k.keyCode)}") }
        controllers.padCapture = { p -> updateSettings { it.copy(uiPadBindings = cc.xdan.frontend.input.UiBindings.withPad(it.uiPadBindings, nav, p)) }; endBinding("$label → ${cc.xdan.frontend.input.UiBindings.padName(p, controllers.activeFamily.value)}") }
    }
    private fun endBinding(msg: String) { keyCapture = null; controllers.padCapture = null; _binding.value = null; toast(msg) }
    fun cancelBinding() { keyCapture = null; controllers.padCapture = null; _binding.value = null; toast("Cancelled") }

    internal fun testDiscoveries(list: List<Discovered>) { _discoveries.value = list }

    /** Adds the chosen discoveries; the rest won't be offered again. */
    fun resolveDiscoveries(accepted: Set<String>) {
        val all = _discoveries.value
        _discoveries.value = emptyList()
        updateSettings { st ->
            var s2 = st
            all.forEach { d ->
                val yes = d.key in accepted
                when {
                    d.emulator != null -> s2 = if (yes) s2.copy(emulators = s2.emulators + d.emulator.toConfig())
                        else s2.copy(dismissedDiscoveries = s2.dismissedDiscoveries + d.key)
                    d.key == "launcher:steam" -> s2 = s2.copy(knownLaunchers = s2.knownLaunchers.orEmpty() + "steam", importSteam = yes)
                    else -> {
                        val id = d.key.removePrefix("launcher:")
                        s2 = s2.copy(knownLaunchers = s2.knownLaunchers.orEmpty() + id,
                            disabledLaunchers = if (yes) s2.disabledLaunchers - id else s2.disabledLaunchers + id,
                            importLaunchers = s2.importLaunchers || yes)
                    }
                }
            }
            s2
        }
        if (accepted.isNotEmpty()) rescan()
    }

    fun rescan(scrapeAfter: Boolean = true) {
        if (_scan.value.running) return
        scope.launch(Dispatchers.IO) {
            _scan.value = ScanState(true)
            val s = settings.value
            val existing = library.value.associateBy { it.id }
            val found = mutableListOf<Discovered>()
            // Emulators first: what's installed decides which systems are scanned.
            if (s.usage != Usage.GAMES) {
                _scan.value = _scan.value.copy(current = "Looking for emulators")
                val have = s.emulators.map { it.path }.toSet()
                runCatching { detector.detect() }.getOrDefault(emptyList())
                    .filter { it.path !in have && "emu:${it.path}" !in s.dismissedDiscoveries }.distinctBy { it.path }
                    .forEach { found += Discovered("emu:${it.path}", it.def.name, "Emulator", it) }
            }
            val allowed = runnablePlatforms()
            refreshRunnable()
            // New files only for systems that can run; games already in the library stay (greyed out if
            // their emulator went away) so favourites, play time and edits survive.
            val roms = RomScanner().scan(settings.value.romFolders, existing, null) { _scan.value = _scan.value.copy(current = it) }.games
                .filter { it.platformId in allowed || it.id in existing }
            _scan.value = _scan.value.copy(current = "Steam")
            val steamLib = SteamLibrary()
            val steamFound = runCatching { steamLib.scan() + steamLib.shortcuts() }.getOrDefault(emptyList())
            // Launchers with games: ask about ones we haven't asked about before.
            val perSource = LauncherSources.Source.entries.associate { it.id to LauncherSources.scan(it) }
            val withGames = perSource.filterValues { it.isNotEmpty() }.keys + listOfNotNull("steam".takeIf { steamFound.isNotEmpty() })
            val known = s.knownLaunchers ?: withGames.also { k -> updateSettings { st -> st.copy(knownLaunchers = k) } }
            val newLaunchers = withGames - known
            newLaunchers.forEach { id ->
                val label = if (id == "steam") "Steam" else LauncherSources.Source.entries.first { it.id == id }.label
                val n = if (id == "steam") steamFound.size else perSource[id]!!.size
                found += Discovered("launcher:$id", label, if (n == 1) "Launcher · 1 game" else "Launcher · $n games")
            }
            if (found.isNotEmpty() && s.onboardingComplete) _discoveries.value = found
            val steamGames = if (s.importSteam && "steam" !in newLaunchers) steamFound else emptyList()
            val steam = steamGames.map { g ->
                existing[g.id]?.let { prev -> prev.copy(path = g.path, installed = true, assets = prev.assets.copy(cover = prev.assets.cover ?: g.assets.cover, hero = prev.assets.hero ?: g.assets.hero, logo = prev.assets.logo ?: g.assets.logo)) } ?: g
            }
            _scan.value = _scan.value.copy(current = "Other launchers")
            val launchers = if (s.importLaunchers) perSource.filterKeys { it !in s.disabledLaunchers && it !in newLaunchers }.values.flatten().distinctBy { it.id }.map { g ->
                existing[g.id]?.let { prev -> prev.copy(path = g.path, assets = prev.assets.copy(cover = prev.assets.cover ?: g.assets.cover, hero = prev.assets.hero ?: g.assets.hero)) } ?: g
            } else emptyList()
            val imported = if (s.importGamelists) Gamelists.apply(roms, s.romFolders) else roms
            val keep = library.value.filter { it.kind == GameKind.PORT }
            // Owned Steam games that aren't installed anywhere (host, CrossOver, Parallels).
            val notInstalled = if (s.showNotInstalled && s.importSteam && "steam" !in newLaunchers) runCatching {
                _scan.value = _scan.value.copy(current = "Steam library")
                val root = steamLib.steamRoot() ?: return@runCatching emptyList()
                val installedIds = (steamFound.map { it.path } + launchers.mapNotNull { it.steamAppId }).toSet()
                val owned = if (s.scraper.steamApiKey.isNotBlank()) SteamOwned.webOwned(http, s.scraper.steamApiKey, root)
                    .ifEmpty { SteamOwned.localIds(root).map { SteamOwned.Owned(it, null) } }
                    else SteamOwned.localIds(root).map { SteamOwned.Owned(it, null) }
                val info = SteamAppInfo.read(File(root, "appcache/appinfo.vdf"), owned.map { it.appId }.toSet())
                SteamOwned.notInstalled(owned, installedIds, info, root).map { g -> existing[g.id]?.copy(installed = false, os = g.os) ?: g }
            }.getOrDefault(emptyList()) else emptyList()
            val all = (imported + steam + launchers + keep + notInstalled).distinctBy { it.id }.filter { it.id !in settings.value.removedGames }
            library.update { all }
            _scan.value = ScanState(false, "", all.size)
            toast("Library updated · ${all.size} games")
            if (scrapeAfter && s.scraper.autoScrapeNew) scraper.scrape(all)
        }
    }

    /**
     * Systems something can actually run: an installed core (GPU cores count when they run in the
     * app or RetroArch is set up) or an enabled emulator that lists the system. Only these are scanned.
     */
    fun runnablePlatforms(s: Settings = settings.value): Set<String> {
        val retroarch = s.emulators.any { it.enabled && it.defId == "retroarch" }
        val viaCores = Platforms.all.filter { p ->
            p.cores.mapNotNull { CoreCatalog.get(it) }.any { c -> CoreCatalog.isInstalled(c.id) && (!c.hw || s.gpuCoresInApp || retroarch) }
        }.map { it.id }
        val viaEmulators = s.emulators.filter { it.enabled }.flatMap { it.platforms }.filter { it != "*" }
        return (viaCores + viaEmulators).toSet()
    }

    // ---------- Installing owned games ----------
    fun installTargets(game: Game): List<InstallTarget> = Installs.targets(game.os, HostOs.current,
        if (HostOs.current == HostOs.WINDOWS) emptyList() else CompatSteam.crossoverSteamBottles(),
        if (HostOs.current == HostOs.MAC) CompatSteam.parallelsSteamVms() else emptyList())

    /** Starts installing [game] in its launcher (Steam on this machine, or Steam in CrossOver/Parallels). */
    fun install(game: Game, target: InstallTarget? = null) {
        val t = target ?: installTargets(game).firstOrNull()
        if (t == null) {
            val host = when (HostOs.current) { HostOs.MAC -> "Mac"; HostOs.LINUX -> "Linux"; HostOs.WINDOWS -> "Windows" }
            toast("${game.title} has no $host version" + if (HostOs.current == HostOs.MAC) " — add Steam to CrossOver or Parallels to install Windows games" else "")
            return
        }
        val cmd = Installs.command(t, game.path)
        if (cmd == null) Launchers.openUri("steam://install/${game.path}")
        else runCatching { ProcessBuilder(listOf(cmd.first) + cmd.second).redirectErrorStream(true).start() }
            .onFailure { toast("Couldn't start ${t.label}: ${it.message}"); return }
        toast("Installing ${game.title} with ${t.label} — it'll appear here when it's done")
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

    fun removeGames(ids: Set<String>) { updateSettings { it.copy(removedGames = it.removedGames + ids) }; library.remove(ids) }

    /**
     * Systems the user plays: those in the library plus those found in their game folders
     * (a quick scan, so setup's BIOS step knows them before the first full scan).
     */
    suspend fun playedPlatforms(): Set<String> = withContext(Dispatchers.IO) {
        library.value.map { it.platformId }.toSet() +
            runCatching { RomScanner().scan(settings.value.romFolders, emptyMap(), runnablePlatforms()).games.map { it.platformId } }.getOrDefault(emptyList())
    }

    /** Remap a RetroPad button for this game or the whole system, applied to the running game at once. */
    fun remap(scopeKey: String, button: RetroButton, pad: Pad) {
        updateSettings { InputMaps.withMapping(it, scopeKey, button, pad) }
        _session.value?.let { it.setInputMap(InputMaps.resolve(settings.value, it.platformId, it.gameId)) }
    }

    fun resetRemap(scopeKey: String) {
        updateSettings { InputMaps.cleared(it, scopeKey) }
        _session.value?.let { it.setInputMap(InputMaps.resolve(settings.value, it.platformId, it.gameId)) }
    }

    /** Detect standalone emulators and add new ones (shared by setup, Settings and controller modes). */
    fun scanEmulators() {
        scope.launch {
            val found = detectEmulators()
            var added = 0
            updateSettings { s ->
                val existing = s.emulators.map { it.path }.toSet()
                val new = found.filter { it.path !in existing }.map { it.toConfig() }
                added = new.size
                s.copy(emulators = s.emulators + new)
            }
            toast(if (found.isEmpty()) "No emulators found" else "Found ${found.size} emulator(s), $added new")
        }
    }

    // ---------- Reset (Settings → About). Game files, saves, states, cores and BIOS are never touched. ----------

    /** Reopen setup, keeping every setting and the library. */
    fun runSetupAgain() {
        updateSettings { it.copy(onboardingComplete = false, onboardingStep = null) }
        mode.value = UiMode.LIST
    }

    /** Back to default settings (setup reopens). The library is kept. */
    fun resetSettings() {
        settings.update { Settings() }
        controllers.nintendoConfirm = false
        mode.value = UiMode.LIST; fullscreen.value = false
    }

    /** Empty the game list (files stay on disk; a rescan rebuilds it). */
    fun clearLibrary() {
        scraper.cancel()
        library.update { emptyList() }
        toast("Library cleared — rescan to bring your games back")
    }

    /** Settings, library, downloaded artwork, caches and saved core options. */
    fun resetEverything() {
        scraper.cancel()
        library.update { emptyList() }
        listOf(AppPaths.media, AppPaths.cache, File(AppPaths.root, "core-options")).forEach { dir ->
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
        resetSettings()
    }

    // ---------- Emulators ----------

    suspend fun detectEmulators(onProgress: (String) -> Unit = {}): List<DetectedEmulator> =
        withContext(Dispatchers.IO) { detector.detect(onProgress) }

    // ---------- Launching ----------

    fun play(game: Game, launcherKey: String? = null) {
        if (!game.installed) { install(game); return }
        if (_session.value != null || _external.value != null) { toast("A game is already running"); return }
        val s = settings.value
        // The full-screen launch screen is a controller-mode thing; the library view keeps its toasts.
        val showLaunch = mode.value != UiMode.LIST
        val t = Launchers.resolve(game, s, launcherKey)
        if (showLaunch && t !is LaunchTarget.Unavailable) {
            val via = if (game.kind == GameKind.PORT) null else t.label
            _launching.value = LaunchingState(game, via, when (t) { is LaunchTarget.Core -> LaunchKind.CORE; is LaunchTarget.External -> LaunchKind.PROCESS; else -> LaunchKind.URI })
        }
        when (t) {
            is LaunchTarget.Core -> {
                val session = EmulationSession(t.file, game.path, game.id, controllers, s.biosDir?.let(::File) ?: AppPaths.system, s.rewind,
                    InputMaps.resolve(s, game.platformId, game.id), game.platformId, resumeAutoSave = s.autoSaveState)
                controllers.gameMode = true
                sessionStart = System.currentTimeMillis()
                _session.value = session
                session.start()
                markPlayed(game)
            }
            is LaunchTarget.External -> launchExternal(game, t, minimizeNow = !showLaunch)
            is LaunchTarget.Steam -> { Launchers.openSteam(t.appId); markPlayed(game); if (!showLaunch) toast("Opening ${game.title} in Steam") }
            is LaunchTarget.Uri -> { Launchers.openUri(t.uri); markPlayed(game); if (!showLaunch) toast("Opening ${game.title} in ${t.label}") }
            is LaunchTarget.Unavailable -> toast(t.reason)
        }
    }

    private fun launchExternal(game: Game, t: LaunchTarget.External, minimizeNow: Boolean) {
        val proc = runCatching {
            ProcessBuilder(t.command).apply { t.workingDir?.let { directory(it) }; redirectErrorStream(true); redirectOutput(ProcessBuilder.Redirect.DISCARD) }.start()
        }.getOrElse { _launching.value = null; toast("Couldn't start ${t.label}: ${it.message}"); return }
        val run = ExternalRun(game, t.label, proc, System.currentTimeMillis())
        _external.value = run
        markPlayed(game)
        if (minimizeNow && settings.value.minimizeOnExternalLaunch) minimizeRequests.tryEmit(true)
        scope.launch(Dispatchers.IO) {
            val code = proc.waitFor()
            val seconds = (System.currentTimeMillis() - run.startedAt) / 1000
            library.updateGame(game.id) { it.copy(playTimeSeconds = it.playTimeSeconds + seconds) }
            _external.value = null
            if (_launching.value?.game?.id == game.id) _launching.value = null
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
