package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
import java.io.File

/**
 * Windows Steam running through a compatibility layer on macOS/Linux:
 * - **CrossOver** bottles (macOS: ~/Library/Application Support/CrossOver/Bottles, Linux: ~/.cxoffice).
 *   Games launch with CrossOver's `wine --bottle <bottle> steam.exe -applaunch <id>`.
 * - **Parallels** Windows VMs whose drives are shared with the Mac (mounted as `/Volumes/[C] …`).
 *   Games launch inside the VM with `prlctl exec <vm> --current-user steam.exe -applaunch <id>`.
 * Library folders inside Windows (`D:\SteamLibrary`) are mapped back to the host through the
 * bottle's dosdevices links / the other shared drives.
 */
object CompatSteam {
    private val home = System.getProperty("user.home")
    private val steamExe = "C:\\Program Files (x86)\\Steam\\steam.exe"

    fun scanAll(): List<Game> = when (HostOs.current) {
        HostOs.MAC -> crossover() + parallels()
        HostOs.LINUX -> crossover()
        HostOs.WINDOWS -> emptyList()
    }

    // ---------------- CrossOver ----------------

    fun crossoverBottlesDir(): File = File(if (HostOs.current == HostOs.MAC) "$home/Library/Application Support/CrossOver/Bottles" else "$home/.cxoffice")

    fun crossoverWine(): File? = listOf(
        "/Applications/CrossOver.app/Contents/SharedSupport/CrossOver/bin/wine",
        "$home/Applications/CrossOver.app/Contents/SharedSupport/CrossOver/bin/wine",
        "/opt/cxoffice/bin/wine",
    ).map(::File).firstOrNull { it.isFile }

    fun crossover(bottles: File = crossoverBottlesDir(), wine: File? = crossoverWine()): List<Game> =
        bottles.listFiles { f -> f.isDirectory }.orEmpty().flatMap { bottle ->
            val driveC = File(bottle, "drive_c")
            val root = listOf("Program Files (x86)/Steam", "Program Files/Steam").map { File(driveC, it) }.firstOrNull { File(it, "steamapps").isDirectory }
                ?: return@flatMap emptyList()
            // Windows path → host path via the bottle's drive links (c: → drive_c, d: → wherever it points).
            fun toHost(winPath: String): File? {
                val m = Regex("^([A-Za-z]):[\\\\/]?(.*)$").find(winPath) ?: return null
                val letter = m.groupValues[1].lowercase()
                val base = if (letter == "c") driveC else File(bottle, "dosdevices/$letter:").let { runCatching { it.canonicalFile }.getOrNull() } ?: return null
                return File(base, m.groupValues[2].replace('\\', '/'))
            }
            installed(root, ::toHost).map { (appId, name) ->
                Game(
                    id = "crossover-steam-${Ids.of(bottle.name)}-$appId", title = name, platformId = Platforms.STEAM, kind = GameKind.LAUNCHER,
                    path = wine?.path ?: "wine",
                    launchArgs = "--bottle \"${bottle.name}\" \"$steamExe\" -applaunch $appId",
                    assets = SteamLibrary().localArt(root, appId),
                )
            }
        }

    // ---------------- Parallels ----------------

    /** Shared Windows drives look like "/Volumes/[C] Windows 11". */
    fun parallels(volumes: File = File("/Volumes"), prlctl: String = "/usr/local/bin/prlctl"): List<Game> {
        val drives = volumes.listFiles { f -> f.isDirectory && Regex("^\\[[A-Z]] ").containsMatchIn(f.name) }.orEmpty()
        return drives.filter { it.name.startsWith("[C] ") }.flatMap { c ->
            val vm = c.name.removePrefix("[C] ").removeSuffix(".hidden").trim()
            val root = listOf("Program Files (x86)/Steam", "Program Files/Steam").map { File(c, it) }.firstOrNull { File(it, "steamapps").isDirectory }
                ?: return@flatMap emptyList()
            fun toHost(winPath: String): File? {
                val m = Regex("^([A-Za-z]):[\\\\/]?(.*)$").find(winPath) ?: return null
                val drive = drives.firstOrNull { it.name.startsWith("[${m.groupValues[1].uppercase()}] ") } ?: return null
                return File(drive, m.groupValues[2].replace('\\', '/'))
            }
            installed(root, ::toHost).map { (appId, name) ->
                Game(
                    id = "parallels-steam-${Ids.of(vm)}-$appId", title = name, platformId = Platforms.STEAM, kind = GameKind.LAUNCHER,
                    path = prlctl, launchArgs = "exec \"$vm\" --current-user \"$steamExe\" -applaunch $appId",
                    assets = SteamLibrary().localArt(root, appId),
                )
            }
        }
    }

    // ---------------- shared ----------------

    /** (appId, name) for installed games across all library folders of a Windows Steam install. */
    fun installed(steamRoot: File, toHost: (String) -> File?): List<Pair<String, String>> {
        val libs = runCatching {
            @Suppress("UNCHECKED_CAST")
            val folders = Vdf.parse(File(steamRoot, "steamapps/libraryfolders.vdf").readText())["libraryfolders"] as? Map<String, Any>
            folders?.values?.mapNotNull { ((it as? Map<*, *>)?.get("path") as? String)?.let(toHost) }
        }.getOrNull().orEmpty() + steamRoot
        return libs.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }.flatMap { lib ->
            File(lib, "steamapps").listFiles { f -> f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }.orEmpty().mapNotNull { acf ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val state = Vdf.parse(acf.readText())["AppState"] as Map<String, Any>
                    val appId = state["appid"] as String
                    val name = state["name"] as String
                    if (SteamLibrary.isTool(appId, name)) null else appId to name
                }.getOrNull()
            }
        }.distinctBy { it.first }
    }
}
