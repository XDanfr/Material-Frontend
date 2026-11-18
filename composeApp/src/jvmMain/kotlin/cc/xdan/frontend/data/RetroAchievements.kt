package cc.xdan.frontend.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * RetroAchievements: identify a ROM by RA's hash, then read the user's progress with the Web API.
 * Cartridge systems only (disc hashing is a different algorithm per system).
 */
object RetroAchievements {
    /** Our platform id → RA console id. */
    val consoles: Map<String, Int> = mapOf(
        "megadrive" to 1, "n64" to 2, "snes" to 3, "gb" to 4, "gba" to 5, "gbc" to 6, "nes" to 7, "pcengine" to 8,
        "sega32x" to 10, "mastersystem" to 11, "lynx" to 13, "ngp" to 14, "gamegear" to 15, "jaguar" to 17,
        "atari2600" to 25, "msx" to 29, "atari7800" to 51, "wonderswan" to 53,
    )

    fun supports(platformId: String) = platformId in consoles

    /** RA hash (lowercase MD5 hex) of a ROM file, or null if the system isn't supported / file unreadable. Zips use their first file. */
    fun hash(file: File, platformId: String): String? {
        if (!supports(platformId)) return null
        val bytes = runCatching {
            if (file.extension.equals("zip", true)) ZipFile(file).use { z ->
                val e = z.entries().asSequence().firstOrNull { !it.isDirectory } ?: return null
                z.getInputStream(e).readBytes()
            } else file.readBytes()
        }.getOrNull() ?: return null
        return md5(normalize(bytes, platformId))
    }

    /** Strips copier headers / fixes byte order the way RA's rcheevos does for each console. */
    fun normalize(b: ByteArray, platformId: String): ByteArray = when (platformId) {
        "nes" -> if (b.size > 16 && b[0] == 'N'.code.toByte() && b[1] == 'E'.code.toByte() && b[2] == 'S'.code.toByte() && b[3] == 0x1A.toByte()) b.copyOfRange(16, b.size) else b
        "snes" -> if (b.size % 8192 == 512) b.copyOfRange(512, b.size) else b
        "pcengine" -> if (b.size % 131072 == 512) b.copyOfRange(512, b.size) else b
        "lynx" -> if (b.size > 64 && String(b, 0, 4, Charsets.ISO_8859_1) == "LYNX") b.copyOfRange(64, b.size) else b
        "atari7800" -> if (b.size > 128 && String(b, 1, 9, Charsets.ISO_8859_1) == "ATARI7800") b.copyOfRange(128, b.size) else b
        "n64" -> n64BigEndian(b)
        else -> b
    }

    /** .z64 is big-endian; .v64 swaps each 16-bit word; .n64 reverses each 32-bit word. */
    private fun n64BigEndian(b: ByteArray): ByteArray {
        if (b.size < 4) return b
        val first = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        val out = b.copyOf()
        when (first) {
            0x37804012 -> for (i in 0 until out.size - 1 step 2) { out[i] = b[i + 1]; out[i + 1] = b[i] }
            0x40123780 -> for (i in 0 until out.size - 3 step 4) { out[i] = b[i + 3]; out[i + 1] = b[i + 2]; out[i + 2] = b[i + 1]; out[i + 3] = b[i] }
        }
        return out
    }

    fun md5(b: ByteArray): String = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    data class Achievement(val title: String, val description: String, val points: Int, val badgeUrl: String, val earned: Boolean, val hardcore: Boolean)
    data class Progress(val gameId: Int, val title: String, val achievements: List<Achievement>) {
        val earned get() = achievements.count { it.earned }
        val total get() = achievements.size
        val points get() = achievements.filter { it.earned }.sumOf { it.points }
        val totalPoints get() = achievements.sumOf { it.points }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** RA game id for a hash (0 = RA doesn't know this dump). */
    suspend fun gameId(http: HttpClient, hash: String): Int? = runCatching {
        val r = http.get("https://retroachievements.org/dorequest.php") { parameter("r", "gameid"); parameter("m", hash) }
        if (!r.status.isSuccess()) return null
        val o = json.parseToJsonElement(r.bodyAsText()).jsonObject
        if (o["Success"]?.jsonPrimitive?.booleanOrNull != true) null else o["GameID"]?.jsonPrimitive?.intOrNull ?: 0
    }.getOrNull()

    suspend fun progress(http: HttpClient, gameId: Int, user: String, key: String): Progress? = runCatching {
        val r = http.get("https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php") {
            parameter("g", gameId); parameter("u", user); parameter("y", key)
        }
        if (!r.status.isSuccess()) return null
        parseProgress(gameId, r.bodyAsText())
    }.getOrNull()

    /** Parses API_GetGameInfoAndUserProgress (Achievements is an object keyed by id). */
    fun parseProgress(gameId: Int, body: String): Progress? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val list = (o["Achievements"] as? JsonObject)?.values?.map { it.jsonObject }.orEmpty().map { a ->
            fun s(k: String) = a[k]?.jsonPrimitive?.contentOrNull
            val earned = !s("DateEarned").isNullOrBlank()
            val badge = s("BadgeName") ?: "00000"
            Achievement(s("Title") ?: "", s("Description") ?: "", a["Points"]?.jsonPrimitive?.intOrNull ?: 0,
                "https://media.retroachievements.org/Badge/$badge${if (earned) "" else "_lock"}.png", earned, !s("DateEarnedHardcore").isNullOrBlank())
        }.sortedWith(compareByDescending<Achievement> { it.earned }.thenBy { it.title })
        return Progress(gameId, o["Title"]?.jsonPrimitive?.contentOrNull ?: "", list)
    }
}
