package app.materialfrontend.data

import app.materialfrontend.model.Game
import app.materialfrontend.model.GameAssets
import app.materialfrontend.model.GameKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Installed games from other PC launchers. Each game's [Game.path] is a launch URI
 * (opened by the OS) or an executable.
 *
 * - Epic Games Launcher: `*.item` manifests (classic launcher on Windows/macOS; the rebuilt
 *   "eglv2" launcher, incl. its native Linux preview, is searched best-effort in its data folders).
 * - EA app (Windows): `__Installer/installerdata.xml` inside each game folder.
 * - Heroic (Epic via Legendary, GOG, Amazon via Nile) on all three OSes.
 * - Lutris (Linux) via its CLI.
 * - Xbox app (Windows): app packages that carry a MicrosoftGame.config (GDK games).
 * - Ubisoft Connect and GOG Galaxy (Windows registry).
 * Steam's non-Steam shortcuts (which also cover Xodus/Nexus "Add to Steam") live in [SteamLibrary].
 */
object LauncherSources {
    private val json = Json { ignoreUnknownKeys = true }
    private val home = System.getProperty("user.home")
    private fun env(k: String) = System.getenv(k)
    private fun JsonElement?.s(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.b(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true

    fun scanAll(): List<Game> = listOf<() -> List<Game>>({ epic() }, ::ea, ::xbox, ::ubisoft, ::gogGalaxy, { heroic() }, ::lutris)
        .flatMap { src -> runCatching { src() }.onFailure { System.err.println("Launcher scan: ${it.message}") }.getOrDefault(emptyList()) }
        .distinctBy { it.id }

    private fun game(id: String, title: String, platform: String, launch: String, assets: GameAssets = GameAssets()) =
        Game(id = id, title = title, platformId = platform, kind = GameKind.LAUNCHER, path = launch, assets = assets)

    // ---------------- Epic Games Launcher ----------------

    private fun epicManifestDirs(): List<File> = buildList {
        when (HostOs.current) {
            HostOs.WINDOWS -> {
                add(File(env("PROGRAMDATA") ?: "C:\\ProgramData", "Epic\\EpicGamesLauncher\\Data\\Manifests"))
                // Rebuilt (Tauri) launcher data folders, if it keeps manifests there.
                listOfNotNull(env("LOCALAPPDATA"), env("APPDATA"), env("PROGRAMDATA")).forEach { add(File(it, "com.epicgames.eglv2")) }
            }
            HostOs.MAC -> {
                add(File(home, "Library/Application Support/Epic/EpicGamesLauncher/Data/Manifests"))
                add(File(home, "Library/Application Support/com.epicgames.eglv2"))
            }
            HostOs.LINUX -> {
                add(File(home, ".local/share/com.epicgames.eglv2"))
                add(File(home, ".config/com.epicgames.eglv2"))
            }
        }
    }.filter { it.isDirectory }

    fun epic(dirs: List<File> = epicManifestDirs()): List<Game> = dirs.flatMap { dir ->
        dir.walkTopDown().maxDepth(4).filter { it.isFile && it.extension == "item" }.mapNotNull { f ->
            runCatching {
                val o = json.parseToJsonElement(f.readText()).jsonObject
                if (o["bIsIncompleteInstall"].b()) return@runCatching null
                val cats = (o["AppCategories"] as? JsonArray)?.mapNotNull { it.s() }.orEmpty()
                if (cats.isNotEmpty() && "games" !in cats) return@runCatching null   // engines, plugins, DLC
                val app = o["AppName"].s() ?: return@runCatching null
                val ns = o["CatalogNamespace"].s().orEmpty(); val item = o["CatalogItemId"].s().orEmpty()
                val uri = "com.epicgames.launcher://apps/" + URLEncoder.encode("$ns:$item:$app", "UTF-8") + "?action=launch&silent=true"
                game("epic-$app", o["DisplayName"].s() ?: app, "epic", uri)
            }.getOrNull()
        }.toList()
    }

    // ---------------- EA app (Windows) ----------------

    /** Default EA/Origin install roots plus the download folder set in the EA app. */
    private fun eaRoots(): List<File> {
        if (HostOs.current != HostOs.WINDOWS) return emptyList()
        val roots = mutableListOf(
            "${env("ProgramFiles") ?: "C:\\Program Files"}\\EA Games",
            "${env("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"}\\Origin Games",
            "${env("ProgramFiles") ?: "C:\\Program Files"}\\Electronic Arts",
        )
        // %LOCALAPPDATA%\Electronic Arts\EA Desktop\user_*.ini → user.downloadinplacedir=…
        File(env("LOCALAPPDATA") ?: "", "Electronic Arts\\EA Desktop").listFiles { f -> f.name.startsWith("user_") && f.name.endsWith(".ini") }
            ?.forEach { ini -> ini.readLines().forEach { l -> if (l.startsWith("user.downloadinplacedir=")) roots += l.substringAfter("=").trim() } }
        return roots.map(::File).filter { it.isDirectory }.distinct()
    }

    fun ea(): List<Game> = eaRoots().flatMap { root ->
        root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            val xml = File(dir, "__Installer/installerdata.xml").takeIf { it.isFile } ?: return@mapNotNull null
            runCatching {
                val text = xml.readText()
                val title = Regex("<gameTitle[^>]*locale=\"en_US\"[^>]*>([^<]+)</gameTitle>").find(text)?.groupValues?.get(1)
                    ?: Regex("<gameTitle[^>]*>([^<]+)</gameTitle>").find(text)?.groupValues?.get(1)
                    ?: dir.name
                // filePath looks like "[HKEY_LOCAL_MACHINE\SOFTWARE\Studio\Game\Install Dir]Game.exe" — the token is the game folder.
                val rel = Regex("<filePath>([^<]+)</filePath>").find(text)?.groupValues?.get(1)?.replace(Regex("^\\[[^]]+]"), "")?.trim()
                val exe = rel?.let { File(dir, it) }?.takeIf { it.isFile } ?: return@runCatching null
                game("ea-" + Ids.of(dir.absolutePath), title.trim(), "ea", exe.absolutePath)
            }.getOrNull()
        }
    }

    // ---------------- Xbox app (Windows) ----------------

    /** GDK games are app packages with a MicrosoftGame.config; launched via shell:AppsFolder. */
    fun xbox(): List<Game> {
        if (HostOs.current != HostOs.WINDOWS) return emptyList()
        val script = """
            ${'$'}r = @(Get-AppxPackage | Where-Object { -not ${'$'}_.IsFramework -and ${'$'}_.InstallLocation -and (Test-Path (Join-Path ${'$'}_.InstallLocation 'MicrosoftGame.config')) } | ForEach-Object {
              ${'$'}cfg = [xml](Get-Content -Raw (Join-Path ${'$'}_.InstallLocation 'MicrosoftGame.config'))
              ${'$'}man = [xml](Get-Content -Raw (Join-Path ${'$'}_.InstallLocation 'AppxManifest.xml'))
              [pscustomobject]@{ pfn = ${'$'}_.PackageFamilyName; name = ${'$'}cfg.Game.ShellVisuals.DefaultDisplayName; pkg = ${'$'}_.Name;
                                 app = @(${'$'}man.Package.Applications.Application)[0].Id; dir = ${'$'}_.InstallLocation } })
            ConvertTo-Json -InputObject ${'$'}r -Compress
        """.trimIndent()
        val out = run("powershell", "-NoProfile", "-NonInteractive", "-Command", script) ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(out.substring(out.indexOf('['))) as JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val g = e.jsonObject
            val pfn = g["pfn"].s() ?: return@mapNotNull null
            val app = g["app"].s() ?: "Game"
            // DefaultDisplayName can be an unresolved ms-resource: reference → use the XboxGames folder name.
            val title = g["name"].s()?.takeUnless { it.startsWith("ms-resource:") }
                ?: g["dir"].s()?.let { File(it).let { d -> if (d.name.equals("Content", true)) d.parentFile?.name else d.name } }
                ?: g["pkg"].s() ?: pfn
            game("xbox-$pfn", title, "xbox-pc", "shell:AppsFolder\\$pfn!$app")
        }
    }

    // ---------------- Ubisoft Connect / GOG Galaxy (Windows registry) ----------------

    /** `reg query KEY /s` → subkey → (value name → data). */
    private fun regTree(key: String): Map<String, Map<String, String>> {
        val out = run("reg", "query", key, "/s", requireJson = false) ?: return emptyMap()
        val tree = LinkedHashMap<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        out.lines().forEach { line ->
            when {
                line.startsWith("HKEY_") -> current = tree.getOrPut(line.trim()) { LinkedHashMap() }
                line.startsWith("    ") -> Regex("^\\s+(.+?)\\s+REG_\\w+\\s*(.*)$").find(line)?.let { m -> current?.put(m.groupValues[1], m.groupValues[2].trim()) }
            }
        }
        return tree
    }

    fun ubisoft(): List<Game> {
        if (HostOs.current != HostOs.WINDOWS) return emptyList()
        return listOf("HKLM\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs", "HKLM\\SOFTWARE\\Ubisoft\\Launcher\\Installs")
            .flatMap { regTree(it).entries }.mapNotNull { (key, values) ->
                val id = key.substringAfterLast('\\').toIntOrNull() ?: return@mapNotNull null
                val dir = values["InstallDir"]?.let(::File)?.takeIf { it.isDirectory } ?: return@mapNotNull null
                game("ubisoft-$id", dir.name, "ubisoft", "uplay://launch/$id/0")
            }.distinctBy { it.id }
    }

    fun gogGalaxy(): List<Game> {
        if (HostOs.current != HostOs.WINDOWS) return emptyList()
        return listOf("HKLM\\SOFTWARE\\WOW6432Node\\GOG.com\\Games", "HKLM\\SOFTWARE\\GOG.com\\Games")
            .flatMap { regTree(it).entries }.mapNotNull { (key, v) ->
                val id = v["gameID"] ?: key.substringAfterLast('\\')
                val exe = v["exe"]?.let(::File)?.takeIf { it.isFile } ?: return@mapNotNull null
                if (v["dependsOn"]?.isNotBlank() == true) return@mapNotNull null   // DLC
                game("gog-$id", v["gameName"] ?: exe.parentFile.name, "gog", exe.absolutePath)
            }.distinctBy { it.id }
    }

    // ---------------- Heroic ----------------

    private fun heroicDirs(): List<File> = when (HostOs.current) {
        HostOs.WINDOWS -> listOfNotNull(env("APPDATA")?.let { "$it\\heroic" })
        HostOs.MAC -> listOf("$home/Library/Application Support/heroic")
        HostOs.LINUX -> listOf("$home/.config/heroic", "$home/.var/app/com.heroicgameslauncher.hgl/config/heroic")
    }.map(::File).filter { it.isDirectory }

    private fun heroicUri(runner: String, app: String) = "heroic://launch?appName=" + URLEncoder.encode(app, "UTF-8") + "&runner=$runner"

    /** app_name → title from Heroic's store cache. */
    private fun heroicTitles(dir: File, file: String, listKey: String): Map<String, String> = runCatching {
        val o = json.parseToJsonElement(File(dir, "store_cache/$file").readText()).jsonObject
        (o[listKey] as? JsonArray)?.mapNotNull { e -> e.jsonObject.let { g -> g["app_name"].s()?.let { it to (g["title"].s() ?: it) } } }?.toMap()
    }.getOrNull().orEmpty()

    fun heroic(dirs: List<File> = heroicDirs()): List<Game> = dirs.flatMap { dir ->
        val out = ArrayList<Game>()
        // Epic via Legendary: { appName: { title, install_path, is_dlc } }
        runCatching {
            val o = json.parseToJsonElement(File(dir, "legendaryConfig/legendary/installed.json").readText()).jsonObject
            o.forEach { (app, v) ->
                val g = v.jsonObject
                if (!g["is_dlc"].b()) out += game("epic-$app", g["title"].s() ?: app, "epic", heroicUri("legendary", app))
            }
        }
        // GOG: { installed: [ { appName, install_path, is_dlc? } ] }
        runCatching {
            val titles = heroicTitles(dir, "gog_library.json", "games")
            val arr = json.parseToJsonElement(File(dir, "gog_store/installed.json").readText()).jsonObject["installed"] as JsonArray
            arr.forEach { e ->
                val g = e.jsonObject
                val app = g["appName"].s() ?: return@forEach
                if (g["is_dlc"].b()) return@forEach
                out += game("gog-$app", titles[app] ?: g["install_path"].s()?.let { File(it).name } ?: app, "gog", heroicUri("gog", app))
            }
        }
        // Amazon via Nile: [ { id, path } ]
        runCatching {
            val titles = heroicTitles(dir, "nile_library.json", "library")
            (json.parseToJsonElement(File(dir, "nile_config/nile/installed.json").readText()) as JsonArray).forEach { e ->
                val g = e.jsonObject
                val app = g["id"].s() ?: return@forEach
                out += game("amazon-$app", titles[app] ?: g["path"].s()?.let { File(it).name } ?: app, "amazon", heroicUri("nile", app))
            }
        }
        out
    }

    // ---------------- Lutris (Linux) ----------------

    fun lutris(): List<Game> {
        if (HostOs.current != HostOs.LINUX) return emptyList()
        val out = run("lutris", "-lo", "--json") ?: run("flatpak", "run", "net.lutris.Lutris", "-lo", "--json") ?: return emptyList()
        val arr = runCatching { json.parseToJsonElement(out.substring(out.indexOf('['))) as JsonArray }.getOrNull() ?: return emptyList()
        val artDirs = listOf("$home/.local/share/lutris", "$home/.cache/lutris", "$home/.var/app/net.lutris.Lutris/data/lutris").map(::File)
        fun art(kind: String, slug: String) = artDirs.flatMap { d -> listOf("jpg", "png").map { File(d, "$kind/$slug.$it") } }.firstOrNull { it.isFile }?.path
        return arr.mapNotNull { e ->
            val g = e.jsonObject
            if (g["runner"].s() == "steam") return@mapNotNull null   // imported from Steam directly
            val id = g["id"].s() ?: return@mapNotNull null
            val slug = g["slug"].s().orEmpty()
            game("lutris-$slug-$id", g["name"].s() ?: slug, "lutris", "lutris:rungameid/$id",
                GameAssets(cover = art("coverart", slug), hero = art("banners", slug)))
        }
    }

    private fun run(vararg cmd: String, requireJson: Boolean = true): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroy(); null }
        else text.takeIf { p.exitValue() == 0 && (!requireJson || it.contains('[')) }
    }.getOrNull()
}
