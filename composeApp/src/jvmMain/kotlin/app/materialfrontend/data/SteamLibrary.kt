package app.materialfrontend.data

import app.materialfrontend.model.Game
import app.materialfrontend.model.GameAssets
import app.materialfrontend.model.GameKind
import app.materialfrontend.model.Platforms
import java.io.File

/** Tiny Valve KeyValues (VDF/ACF text) parser. */
object Vdf {
    fun parse(text: String): Map<String, Any> {
        val tokens = tokenize(text)
        var i = 0
        fun obj(): Map<String, Any> {
            val m = LinkedHashMap<String, Any>()
            while (i < tokens.size) {
                val t = tokens[i++]
                if (t == "}") return m
                val next = tokens.getOrNull(i) ?: break
                if (next == "{") { i++; m[t] = obj() } else { i++; m[t] = next }
            }
            return m
        }
        return obj()
    }

    private fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && s.getOrNull(i + 1) == '/' -> { while (i < s.length && s[i] != '\n') i++ }
                c == '{' || c == '}' -> { out += c.toString(); i++ }
                c == '"' -> {
                    val sb = StringBuilder(); i++
                    while (i < s.length && s[i] != '"') {
                        if (s[i] == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2 } else sb.append(s[i++])
                    }
                    i++; out += sb.toString()
                }
                else -> { val st = i; while (i < s.length && !s[i].isWhitespace() && s[i] != '{' && s[i] != '}') i++; out += s.substring(st, i) }
            }
        }
        return out
    }
}

class SteamLibrary {
    private val home = System.getProperty("user.home")

    fun steamRoot(): File? {
        val candidates = when (HostOs.current) {
            HostOs.WINDOWS -> listOfNotNull(registrySteamPath(), "C:\\Program Files (x86)\\Steam", "C:\\Program Files\\Steam")
            HostOs.MAC -> listOf("$home/Library/Application Support/Steam")
            HostOs.LINUX -> listOf("$home/.steam/steam", "$home/.local/share/Steam", "$home/.var/app/com.valvesoftware.Steam/.local/share/Steam", "$home/snap/steam/common/.local/share/Steam")
        }
        return candidates.map(::File).firstOrNull { File(it, "steamapps").isDirectory }
    }

    private fun registrySteamPath(): String? = runCatching {
        val p = ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText(); p.waitFor()
        Regex("SteamPath\\s+REG_SZ\\s+(.+)").find(out)?.groupValues?.get(1)?.trim()?.replace('/', '\\')
    }.getOrNull()

    /** Non-game app ids / names to skip (runtimes, Proton, redistributables). */
    private val skipIds = setOf("228980", "1070560", "1391110", "1628350", "1493710", "1826330", "2180100", "250820", "1887720", "2348590", "961940", "1245040", "1420170", "1580130", "2805730", "3658110")
    private val skipName = Regex("(?i)proton|steam linux runtime|steamworks|redistributable|soundtrack|dedicated server|sdk|steamvr")

    fun scan(): List<Game> {
        val root = steamRoot() ?: return emptyList()
        val libs = runCatching {
            @Suppress("UNCHECKED_CAST")
            val folders = Vdf.parse(File(root, "steamapps/libraryfolders.vdf").readText())["libraryfolders"] as? Map<String, Any>
            folders?.values?.mapNotNull { (it as? Map<*, *>)?.get("path") as? String }
        }.getOrNull().orEmpty().ifEmpty { listOf(root.path) }

        return libs.distinct().flatMap { lib ->
            File(lib, "steamapps").listFiles { f -> f.name.startsWith("appmanifest_") && f.name.endsWith(".acf") }.orEmpty().mapNotNull { acf ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val state = Vdf.parse(acf.readText())["AppState"] as Map<String, Any>
                    val appId = state["appid"] as String
                    val name = state["name"] as String
                    if (appId in skipIds || skipName.containsMatchIn(name)) return@runCatching null
                    Game(
                        id = "steam-$appId",
                        title = name,
                        platformId = Platforms.STEAM,
                        kind = GameKind.STEAM,
                        path = appId,
                        fileSize = (state["SizeOnDisk"] as? String)?.toLongOrNull() ?: 0,
                        lastPlayed = (state["LastPlayed"] as? String)?.toLongOrNull()?.takeIf { it > 0 }?.times(1000),
                        assets = localArt(root, appId),
                    )
                }.getOrNull()
            }
        }
    }

    /** Steam keeps library art locally; layouts differ by client version. */
    fun localArt(root: File, appId: String): GameAssets {
        val cache = File(root, "appcache/librarycache")
        fun find(vararg names: String): String? {
            names.forEach { n ->
                File(cache, "${appId}_$n").takeIf { it.isFile }?.let { return it.path }
                File(cache, "$appId/$n").takeIf { it.isFile }?.let { return it.path }
            }
            // Newer clients nest files under hashed sub-folders.
            File(cache, appId).walkTopDown().maxDepth(2).firstOrNull { f -> f.isFile && names.any { f.name == it } }?.let { return it.path }
            return null
        }
        return GameAssets(
            cover = find("library_600x900_2x.jpg", "library_600x900.jpg", "library_capsule.jpg"),
            hero = find("library_hero.jpg", "library_hero_blur.jpg"),
            logo = find("logo.png", "logo_2x.png"),
            icon = find("icon.jpg"),
        )
    }

    companion object {
        fun cdnArt(appId: String) = GameAssets(
            cover = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900_2x.jpg",
            hero = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_hero.jpg",
            logo = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/logo.png",
            screenshot = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg",
        )
    }
}
