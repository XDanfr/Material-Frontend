package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.GZIPInputStream

/** Battle.net (product.db) and itch.io (install receipts). */
object MoreLaunchers {
    private val home = System.getProperty("user.home")

    // ---------------- Battle.net ----------------

    /** Agent uid → (title, battlenet:// launch code). Unknown uids are skipped (they're tools, betas, or the launcher itself). */
    val blizzard: Map<String, Pair<String, String>> = mapOf(
        "wow" to ("World of Warcraft" to "WoW"), "wow_classic" to ("World of Warcraft Classic" to "WoWC"),
        "wow_classic_era" to ("WoW Classic Era" to "WoWC"), "s2" to ("StarCraft II" to "S2"), "s1" to ("StarCraft" to "S1"),
        "d3" to ("Diablo III" to "D3"), "fenris" to ("Diablo IV" to "Fen"), "osi" to ("Diablo II: Resurrected" to "OSI"),
        "hs_beta" to ("Hearthstone" to "WTCG"), "heroes" to ("Heroes of the Storm" to "Hero"), "prometheus" to ("Overwatch 2" to "Pro"),
        "w3" to ("Warcraft III: Reforged" to "W3"), "rtro" to ("Blizzard Arcade Collection" to "RTRO"), "wlby" to ("Crash Bandicoot 4" to "WLBY"),
        "viper" to ("Call of Duty: Black Ops 4" to "VIPR"), "odin" to ("Call of Duty: Modern Warfare" to "ODIN"),
        "lazarus" to ("Call of Duty: MW2 Campaign Remastered" to "LAZR"), "zeus" to ("Call of Duty: Black Ops Cold War" to "ZEUS"),
        "fore" to ("Call of Duty: Vanguard" to "FORE"), "auks" to ("Call of Duty" to "AUKS"), "anbs" to ("Diablo Immortal" to "ANBS"),
        "gryphon" to ("Warcraft Rumble" to "GRY"), "scor" to ("Sea of Thieves" to "SCOR"),
    )

    private fun productDb(): File? = when (HostOs.current) {
        HostOs.MAC -> File("/Users/Shared/Battle.net/Agent/product.db")
        HostOs.WINDOWS -> File((System.getenv("ProgramData") ?: "C:\\ProgramData") + "\\Battle.net\\Agent\\product.db")
        HostOs.LINUX -> null
    }?.takeIf { it.isFile }

    fun battleNet(db: File? = productDb()): List<Game> {
        val bytes = db?.let { runCatching { it.readBytes() }.getOrNull() } ?: return emptyList()
        return parseProductDb(bytes).mapNotNull { (uid, path) ->
            val (title, code) = blizzard[uid] ?: return@mapNotNull null
            if (path.isNotBlank() && !File(path).exists() && HostOs.current != HostOs.WINDOWS) return@mapNotNull null
            Game(id = "battlenet-$uid", title = title, platformId = "battlenet", kind = GameKind.LAUNCHER, path = "battlenet://$code")
        }.distinctBy { it.id }
    }

    /** product.db is protobuf: Database{1: repeated ProductInstall{1: uid, 2: product_code, 3: Settings{1: install_path}}}. */
    fun parseProductDb(b: ByteArray): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((field, msg) in Proto(b).fields()) {
            if (field != 1 || msg !is ByteArray) continue
            var uid = ""; var path = ""
            for ((f, v) in Proto(msg).fields()) {
                if (f == 1 && v is ByteArray) uid = String(v)
                if (f == 3 && v is ByteArray) Proto(v).fields().firstOrNull { it.first == 1 && it.second is ByteArray }?.let { path = String(it.second as ByteArray) }
            }
            if (uid.isNotBlank()) out += uid to path
        }
        return out
    }

    /** Just enough protobuf: varints and length-delimited fields. */
    private class Proto(private val b: ByteArray) {
        private var i = 0
        private fun varint(): Long { var r = 0L; var s = 0; while (i < b.size) { val x = b[i++].toInt() and 0xFF; r = r or ((x and 0x7F).toLong() shl s); if (x < 0x80) break; s += 7 }; return r }
        fun fields(): List<Pair<Int, Any>> {
            val out = ArrayList<Pair<Int, Any>>()
            runCatching {
                while (i < b.size) {
                    val key = varint(); val f = (key ushr 3).toInt()
                    when ((key and 7).toInt()) {
                        0 -> out += f to varint()
                        1 -> { i += 8 }
                        2 -> { val n = varint().toInt(); if (n < 0 || i + n > b.size) return out; out += f to b.copyOfRange(i, i + n); i += n }
                        5 -> { i += 4 }
                        else -> return out
                    }
                }
            }
            return out
        }
    }

    // ---------------- itch.io ----------------

    private fun itchAppDirs(): List<File> = when (HostOs.current) {
        HostOs.MAC -> listOf("$home/Library/Application Support/itch/apps")
        HostOs.WINDOWS -> listOfNotNull(System.getenv("APPDATA")?.let { "$it\\itch\\apps" })
        HostOs.LINUX -> listOf("$home/.config/itch/apps")
    }.map(::File).filter { it.isDirectory }

    private val json = Json { ignoreUnknownKeys = true }

    /** Each itch install keeps .itch/receipt.json.gz with the game's title; launch the game directly. */
    fun itch(dirs: List<File> = itchAppDirs(), os: HostOs = HostOs.current): List<Game> = dirs.flatMap { root ->
        root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            val receipt = File(dir, ".itch/receipt.json.gz").takeIf { it.isFile } ?: return@mapNotNull null
            val o = runCatching { json.parseToJsonElement(GZIPInputStream(receipt.inputStream()).bufferedReader().readText()).jsonObject }.getOrNull() ?: return@mapNotNull null
            val g = o["game"] as? JsonObject
            val title = g?.get("title")?.jsonPrimitive?.contentOrNull ?: dir.name
            val gameId = g?.get("id")?.jsonPrimitive?.contentOrNull ?: dir.name
            val launch = launchable(dir, os) ?: return@mapNotNull null
            Game(id = "itch-$gameId", title = title, platformId = "itch", kind = GameKind.LAUNCHER,
                path = if (launch.extension == "app") launch.toURI().toString() else launch.path)
        }
    }

    /** The game's .app (Mac), .exe (Windows) or executable (Linux), preferring the shallowest. */
    fun launchable(dir: File, os: HostOs): File? {
        val skip = Regex("(?i)unins|crash|setup|redist|vcredist|dxsetup|UnityCrashHandler")
        // Don't look inside .itch or inside app bundles (but the bundles themselves count).
        fun insideBundle(f: File) = generateSequence(f.parentFile) { it.parentFile }.takeWhile { it != dir }.any { it.name.endsWith(".app") || it.name == ".itch" }
        return dir.walkTopDown().maxDepth(3)
            .filter { f -> !insideBundle(f) && !skip.containsMatchIn(f.name) && when (os) {
                HostOs.MAC -> f.isDirectory && f.name.endsWith(".app")
                HostOs.WINDOWS -> f.isFile && f.extension.equals("exe", true)
                HostOs.LINUX -> f.isFile && f.canExecute() && (f.extension.isEmpty() || f.extension in setOf("x86_64", "sh", "AppImage"))
            } }
            .minByOrNull { it.path.count { c -> c == File.separatorChar } }
    }
}
