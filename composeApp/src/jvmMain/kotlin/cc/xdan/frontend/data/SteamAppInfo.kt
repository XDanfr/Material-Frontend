package cc.xdan.frontend.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Steam's local app metadata cache (appcache/appinfo.vdf, binary VDF v27–v29). Gives every known
 * app's name, type and supported OSes without going online.
 */
object SteamAppInfo {
    data class App(val id: String, val name: String, val type: String, val oslist: Set<String>, val logoPosition: String? = null)

    private const val V27 = 0x07564427; private const val V28 = 0x07564428; private const val V29 = 0x07564429

    fun read(file: File, only: Set<String>? = null): Map<String, App> = runCatching { parse(file.readBytes(), only) }.getOrDefault(emptyMap())

    fun parse(bytes: ByteArray, only: Set<String>? = null): Map<String, App> {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = b.int; b.int   // universe
        require(magic == V27 || magic == V28 || magic == V29) { "Unknown appinfo version" }
        val strings: List<String>? = if (magic == V29) {
            val off = b.long.toInt()
            val t = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).position(off) as ByteBuffer
            val n = t.int
            List(n) { cstr(t) }
        } else null
        val out = LinkedHashMap<String, App>()
        while (b.remaining() >= 4) {
            val appId = b.int
            if (appId == 0) break
            val size = b.int
            val end = b.position() + size
            val id = appId.toString()
            if (only == null || id in only) {
                b.position(b.position() + 4 + 4 + 8 + 20 + 4 + (if (magic == V27) 0 else 20))
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val root = readObject(b, strings)
                    val common = ((root["appinfo"] as? Map<String, Any>) ?: root)["common"] as? Map<String, Any>
                    if (common != null) {
                        val name = common["name"] as? String
                        if (name != null) out[id] = App(id, name, (common["type"] as? String)?.lowercase() ?: "",
                            (common["oslist"] as? String)?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                            ((common["library_assets"] as? Map<String, Any>)?.get("logo_position") as? Map<String, Any>)?.get("pinned_position") as? String)
                    }
                }
            }
            b.position(end)
        }
        return out
    }

    private fun cstr(b: ByteBuffer): String {
        val start = b.position()
        while (b.get() != 0.toByte()) { /* scan */ }
        return String(b.array(), start, b.position() - start - 1, Charsets.UTF_8)
    }

    private fun readObject(b: ByteBuffer, strings: List<String>?): Map<String, Any> {
        val m = LinkedHashMap<String, Any>()
        while (true) {
            val type = b.get().toInt()
            if (type == 0x08) return m
            val key = if (strings != null) strings[b.int] else cstr(b)
            when (type) {
                0x00 -> m[key] = readObject(b, strings)
                0x01 -> m[key] = cstr(b)
                0x02 -> m[key] = b.int
                0x03 -> m[key] = b.float
                0x04, 0x06 -> m[key] = b.int
                0x07 -> m[key] = b.long
                0x0A -> m[key] = b.long
                0x05 -> { // UTF-16 string, null-terminated
                    val sb = StringBuilder(); while (true) { val c = b.char; if (c == '\u0000') break; sb.append(c) }; m[key] = sb.toString()
                }
                else -> error("Unknown KV type $type")
            }
        }
    }
}
