package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameAssets
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
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

/** Binary KeyValues (shortcuts.vdf): 0x00 map, 0x01 string, 0x02 int32, 0x07 uint64, 0x08 end. */
object BinaryVdf {
    fun parse(b: ByteArray): Map<String, Any> {
        var i = 0
        fun cstr(): String { val st = i; while (i < b.size && b[i] != 0.toByte()) i++; return String(b, st, i - st, Charsets.UTF_8).also { i++ } }
        fun int32(): Int { val v = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24); i += 4; return v }
        fun map(): Map<String, Any> {
            val m = LinkedHashMap<String, Any>()
            while (i < b.size) {
                val type = b[i++].toInt()
                if (type == 0x08) return m
                val key = cstr()
                when (type) {
                    0x00 -> m[key] = map()
                    0x01 -> m[key] = cstr()
                    0x02 -> m[key] = int32()
                    0x07 -> { m[key] = int32().toLong() and 0xffffffffL or (int32().toLong() shl 32) }
                    else -> return m
                }
            }
            return m
        }
        return map()
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
    private val skipIds get() = SKIP_IDS
    private val skipName get() = SKIP_NAME



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

    /**
     * Non-Steam games added to Steam (userdata/<user>/config/shortcuts.vdf). Covers anything users
     * "Add to Steam" — e.g. the Xodus-based Nexus launcher for Xbox PC games on Linux, EmuDeck tools.
     * Launched through Steam with the shortcut's game id.
     */
    fun shortcuts(): List<Game> {
        val root = steamRoot() ?: return emptyList()
        return File(root, "userdata").listFiles { f -> f.isDirectory }.orEmpty().flatMap { user ->
            val vdf = File(user, "config/shortcuts.vdf").takeIf { it.isFile } ?: return@flatMap emptyList()
            val grid = File(user, "config/grid")
            @Suppress("UNCHECKED_CAST")
            val list = runCatching { (BinaryVdf.parse(vdf.readBytes())["shortcuts"] as? Map<String, Any>)?.values }.getOrNull().orEmpty()
            list.mapNotNull { e ->
                val m = (e as? Map<*, *>)?.mapKeys { (it.key as String).lowercase() } ?: return@mapNotNull null
                val name = m["appname"] as? String ?: return@mapNotNull null
                val exe = (m["exe"] as? String).orEmpty()
                if (exe.contains("material-frontend", true) || exe.contains("Material Frontend", true)) return@mapNotNull null
                val appId = ((m["appid"] as? Int) ?: return@mapNotNull null).toLong() and 0xffffffffL
                val gameId = (appId shl 32) or 0x02000000L
                fun art(suffix: String) = listOf("png", "jpg").map { File(grid, "$appId$suffix.$it") }.firstOrNull { it.isFile }?.path
                Game(
                    id = "steam-shortcut-$appId", title = name, platformId = Platforms.STEAM, kind = GameKind.STEAM,
                    path = gameId.toString(), assets = GameAssets(cover = art("p"), hero = art("_hero"), logo = art("_logo"), screenshot = art("")),
                )
            }
        }.distinctBy { it.id }
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
        /** Non-game app ids / names to skip (runtimes, Proton, redistributables). */
        val SKIP_IDS = setOf("480", "228980", "1070560", "1391110", "1628350", "1493710", "1826330", "2180100", "250820", "1887720", "2348590", "961940", "1245040", "1420170", "1580130", "2805730", "3658110")
        val SKIP_NAME = Regex("(?i)proton|steam linux runtime|steamworks|redistributable|soundtrack|dedicated server|sdk|steamvr")
        fun isTool(appId: String, name: String) = appId in SKIP_IDS || SKIP_NAME.containsMatchIn(name)

        fun cdnArt(appId: String) = GameAssets(
            cover = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900_2x.jpg",
            hero = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_hero.jpg",
            logo = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/logo.png",
            screenshot = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/header.jpg",
        )
    }
}
