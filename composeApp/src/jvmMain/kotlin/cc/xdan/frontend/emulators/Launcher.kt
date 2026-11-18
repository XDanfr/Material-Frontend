package cc.xdan.frontend.emulators

import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.model.EmulatorConfig
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.Settings
import java.io.File

/** How a game will be started. */
sealed interface LaunchTarget {
    val label: String
    /** In-app libretro core. */
    data class Core(val core: CoreDef, val file: File) : LaunchTarget { override val label = core.name }
    /** External process (standalone emulator, RetroArch with a HW core, a port). */
    data class External(val name: String, val command: List<String>, val workingDir: File?) : LaunchTarget { override val label = name }
    data class Steam(val appId: String) : LaunchTarget { override val label = "Steam" }
    /** Opened by the OS (launcher URI like com.epicgames.launcher://, heroic://, uplay://, shell:AppsFolder…). */
    data class Uri(val uri: String, override val label: String) : LaunchTarget
    data class Unavailable(val reason: String) : LaunchTarget { override val label = "Unavailable" }
}

/** "Launches with mGBA", or why it can't launch yet. */
fun launchLabel(t: LaunchTarget): String = when (t) {
    is LaunchTarget.Unavailable -> "${t.reason} — install a core or add an emulator in Settings"
    else -> "Launches with ${t.label}"
}

object Launchers {
    /** Every way this game could be launched, best first. Keys are "core:<id>" / "emu:<configId>". */
    fun options(game: Game, settings: Settings): List<Pair<String, LaunchTarget>> {
        if (game.kind == GameKind.STEAM) return listOf("steam" to LaunchTarget.Steam(game.path))
        if (game.kind == GameKind.LAUNCHER) {
            val store = when {
                game.id.startsWith("crossover-") -> "${Platforms.get(game.platformId).name} in CrossOver"
                game.id.startsWith("parallels-") -> "${Platforms.get(game.platformId).name} in Parallels"
                else -> Platforms.get(game.platformId).name
            }
            return if (isUri(game.path)) listOf("launcher" to LaunchTarget.Uri(game.path, store))
            else File(game.path).let { exe -> listOf("launcher" to LaunchTarget.External(store, listOf(exe.path) + splitArgs(game.launchArgs.orEmpty()), exe.parentFile?.takeIf { it.isDirectory })) }
        }
        if (game.kind == GameKind.PORT) {
            val exe = File(game.path)
            return listOf("port" to LaunchTarget.External(exe.nameWithoutExtension, listOf(exe.path) + splitArgs(game.launchArgs.orEmpty()), exe.parentFile))
        }
        val platform = Platforms.get(game.platformId)
        val out = ArrayList<Pair<String, LaunchTarget>>()
        val retroarch = settings.emulators.firstOrNull { it.defId == "retroarch" && it.enabled }
        platform.cores.mapNotNull { CoreCatalog.get(it) }.forEach { core ->
            val file = CoreCatalog.installedFile(core.id) ?: return@forEach
            // GPU cores run in the app through an OpenGL context (unless turned off); RetroArch stays available.
            if (!core.hw || settings.gpuCoresInApp) out += "core:${core.id}" to LaunchTarget.Core(core, file)
            if (core.hw && retroarch != null) out += "retroarch:${core.id}" to external(retroarch, game, file, "${core.name} (RetroArch)")
        }
        settings.emulators.filter { it.enabled && (game.platformId in it.platforms || "*" in it.platforms) && it.defId != "retroarch" }
            .sortedBy { e -> platform.emulators.indexOf(e.defId).let { if (it < 0) 99 else it } }
            .forEach { out += "emu:${it.id}" to external(it, game, null, it.name) }
        return out
    }

    fun resolve(game: Game, settings: Settings, override: String? = null): LaunchTarget {
        val opts = options(game, settings)
        val key = override ?: game.launcherOverride ?: settings.defaultLauncher[game.platformId]
        return opts.firstOrNull { it.first == key }?.second
            ?: opts.firstOrNull()?.second
            ?: LaunchTarget.Unavailable("No core or emulator installed for ${Platforms.get(game.platformId).name}")
    }

    fun external(cfg: EmulatorConfig, game: Game, core: File?, label: String): LaunchTarget.External {
        val rom = File(game.path)
        val tokens = mapOf(
            "{rom}" to rom.path, "{romdir}" to (rom.parent ?: ""), "{romname}" to rom.nameWithoutExtension,
            "{core}" to (core?.path ?: ""),
        )
        val args = splitArgs(cfg.args).map { a -> tokens.entries.fold(a) { acc, (k, v) -> acc.replace(k, v) } }.filter { it.isNotEmpty() }
        val base = when {
            cfg.flatpakId != null -> listOf("flatpak", "run", cfg.flatpakId)
            HostOs.current == HostOs.MAC && cfg.path.endsWith(".app") -> listOf(EmulatorDetector().bundleExecutable(File(cfg.path))?.path ?: cfg.path)
            else -> listOf(cfg.path)
        }
        return LaunchTarget.External(label, base + args, File(cfg.path).parentFile?.takeIf { it.isDirectory })
    }

    /** Splits a template respecting double quotes; quotes are stripped. */
    fun splitArgs(s: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder(); var q = false; var any = false
        for (c in s) when {
            c == '"' -> { q = !q; any = true }
            c.isWhitespace() && !q -> { if (cur.isNotEmpty() || any) out += cur.toString(); cur.clear(); any = false }
            else -> cur.append(c)
        }
        if (cur.isNotEmpty() || any) out += cur.toString()
        return out
    }

    fun openSteam(appId: String) = openUri("steam://rungameid/$appId")

    /** A scheme like "heroic:" / "lutris:" / "shell:" — but not a Windows drive letter ("C:"). */
    fun isUri(s: String) = Regex("^[A-Za-z][A-Za-z0-9+.-]+:").containsMatchIn(s)

    fun openUri(uri: String) {
        val cmd = when (HostOs.current) {
            HostOs.WINDOWS -> listOf("cmd", "/c", "start", "", uri)
            HostOs.MAC -> listOf("open", uri)
            HostOs.LINUX -> listOf("xdg-open", uri)
        }
        runCatching { ProcessBuilder(cmd).start() }
    }

    fun reveal(file: File) {
        val cmd = when (HostOs.current) {
            HostOs.WINDOWS -> listOf("explorer", "/select,", file.path)
            HostOs.MAC -> listOf("open", "-R", file.path)
            HostOs.LINUX -> listOf("xdg-open", (if (file.isDirectory) file else file.parentFile).path)
        }
        runCatching { ProcessBuilder(cmd).start() }
    }
}
