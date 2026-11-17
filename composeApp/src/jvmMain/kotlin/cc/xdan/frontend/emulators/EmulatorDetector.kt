package cc.xdan.frontend.emulators

import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.model.EmulatorConfig
import java.io.File
import java.util.concurrent.TimeUnit

data class DetectedEmulator(val def: EmulatorDef, val path: String, val source: String, val flatpakId: String? = null) {
    fun toConfig() = EmulatorConfig(
        id = def.id + "-" + Integer.toHexString(path.hashCode()),
        defId = def.id, name = def.name, path = path, args = def.args, platforms = def.platforms, flatpakId = flatpakId,
    )
}

/** Searches common install locations, PATH, app bundles, AppImage folders and Flatpak. */
class EmulatorDetector {
    private val home = System.getProperty("user.home")

    fun detect(onProgress: (String) -> Unit = {}): List<DetectedEmulator> {
        val found = LinkedHashMap<String, DetectedEmulator>()
        fun add(d: DetectedEmulator) { found.putIfAbsent(d.def.id + "|" + d.path, d) }

        // 1. Flatpak (Linux)
        if (HostOs.current == HostOs.LINUX) {
            onProgress("Flatpak")
            val apps = run("flatpak", "list", "--app", "--columns=application")?.lines()?.map { it.trim() }?.toSet().orEmpty()
            EmulatorCatalog.all.forEach { def -> def.flatpak.firstOrNull { it in apps }?.let { add(DetectedEmulator(def, "flatpak:$it", "Flatpak", it)) } }
        }

        // 2. macOS application bundles
        if (HostOs.current == HostOs.MAC) {
            onProgress("Applications")
            val bundles = listOf("/Applications", "$home/Applications", "/Applications/Emulators", "$home/Applications/Emulators")
                .flatMap { File(it).listFiles { f -> f.name.endsWith(".app") }.orEmpty().toList() }
            for (def in EmulatorCatalog.all) {
                bundles.firstOrNull { b -> def.macApps.any { b.name.removeSuffix(".app").equals(it, true) } }
                    ?.let { bundleExecutable(it) }?.let { add(DetectedEmulator(def, it.path, "Applications")) }
            }
        }

        // 3. PATH
        onProgress("PATH")
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator).map(::File).filter { it.isDirectory }
        for (def in EmulatorCatalog.all) for (dir in pathDirs) {
            matchIn(dir, def, depth = 0)?.let { add(DetectedEmulator(def, it.path, "PATH")) }
        }

        // 4. Common folders (depth-limited walk)
        for (dir in searchRoots()) {
            onProgress(dir.path)
            val files = collect(dir, maxDepth = if (dir.path.contains("Program Files")) 2 else 3)
            for (def in EmulatorCatalog.all) {
                files.firstOrNull { matches(it, def) }?.let { add(DetectedEmulator(def, it.path, dir.name)) }
            }
        }
        return found.values.sortedBy { it.def.name }
    }

    private fun searchRoots(): List<File> = when (HostOs.current) {
        HostOs.WINDOWS -> listOfNotNull(
            System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs" }, System.getenv("LOCALAPPDATA"),
            System.getenv("APPDATA"), "$home\\scoop\\apps", "$home\\Emulation", "$home\\Emulators",
            "C:\\Emulation", "C:\\Emulators", "C:\\RetroArch-Win64", "$home\\Desktop", "$home\\Downloads",
        )
        HostOs.MAC -> listOf("/opt/homebrew/bin", "/usr/local/bin", "$home/Emulation", "$home/Emulators", "$home/Downloads")
        HostOs.LINUX -> listOf(
            "$home/Applications", "$home/AppImages", "$home/.local/bin", "$home/Emulation/tools", "$home/Emulation",
            "$home/Emulators", "$home/Downloads", "$home/Desktop", "/opt", "/usr/games", "/snap/bin",
            // Decky / EmuDeck / RetroDECK locations
            "$home/homebrew", "$home/.var/app/net.retrodeck.retrodeck",
        )
    }.map(::File).filter { it.isDirectory }

    private fun collect(dir: File, maxDepth: Int): List<File> =
        dir.walkTopDown().maxDepth(maxDepth)
            .onEnter { !it.name.startsWith(".") && it.name !in skipDirs && !(HostOs.current == HostOs.MAC && it.name.endsWith(".app")) }
            .filter { it.isFile }.take(20000).toList()

    private val skipDirs = setOf("node_modules", "Windows", "WindowsApps", "Microsoft", "Common Files", "Steam", "steamapps", "cache", "Cache", "Packages")

    private fun matchIn(dir: File, def: EmulatorDef, depth: Int): File? =
        dir.listFiles()?.firstOrNull { it.isFile && matches(it, def) }

    private fun matches(f: File, def: EmulatorDef): Boolean {
        val n = f.name.lowercase()
        return when (HostOs.current) {
            HostOs.WINDOWS -> n.endsWith(".exe") && def.binaries.any { n == "$it.exe" }
            HostOs.LINUX -> {
                val isAppImage = n.endsWith(".appimage")
                val stem = n.removeSuffix(".appimage")
                (isAppImage && def.binaries.any { stem == it || stem.startsWith("$it-") || stem.startsWith("${it}_") || stem.startsWith("$it.") }) ||
                    (!n.contains('.') && f.canExecute() && def.binaries.any { n == it })
            }
            HostOs.MAC -> !n.contains('.') && f.canExecute() && def.binaries.any { n == it }
        }
    }

    /** Resolves Foo.app/Contents/MacOS/<CFBundleExecutable>. */
    fun bundleExecutable(app: File): File? {
        val plist = File(app, "Contents/Info.plist")
        val exeName = runCatching {
            Regex("<key>CFBundleExecutable</key>\\s*<string>([^<]+)</string>").find(plist.readText())?.groupValues?.get(1)
        }.getOrNull()
        val macos = File(app, "Contents/MacOS")
        return exeName?.let { File(macos, it) }?.takeIf { it.isFile } ?: macos.listFiles()?.firstOrNull { it.isFile && it.canExecute() }
    }

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroy(); null } else out
    }.getOrNull()
}
