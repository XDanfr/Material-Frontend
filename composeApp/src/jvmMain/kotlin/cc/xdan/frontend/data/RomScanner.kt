package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.GameMeta
import cc.xdan.frontend.model.Platform
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.RomFolder
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

object Ids {
    fun of(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(16)
}

object TitleCleaner {
    private val tags = Regex("""\s*[\(\[][^)\]]*[)\]]""")
    private val region = Regex("""\((USA|Europe|Japan|World|U|E|J|Germany|France|Spain|Italy|Korea|Brazil|Australia|Asia)(,[^)]*)?\)""", RegexOption.IGNORE_CASE)

    /** "Legend of Zelda, The - A Link to the Past (USA) [!]" -> "The Legend of Zelda - A Link to the Past" */
    fun clean(fileStem: String): String {
        var t = fileStem.replace('_', ' ').replace(tags, "").trim()
        val m = Regex("""^(.*), (The|A|An)( - .*|: .*)?$""").find(t)
        if (m != null) t = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
        return t.replace(Regex("\\s+"), " ").ifBlank { fileStem }
    }

    fun region(fileStem: String): String? = region.find(fileStem)?.groupValues?.get(1)

    /** Lowercase alphanumerics only, for fuzzy matching. */
    fun key(s: String) = clean(s).lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")
}

/**
 * Walks ROM folders and turns files (or directory games) into [Game]s. Platform comes from the
 * folder override, then any path segment matching a platform alias, then a unique extension.
 */
class RomScanner {
    data class Result(val games: List<Game>, val unknownFiles: Int)

    /**
     * @param allowed systems that something can actually run (installed core or enabled emulator);
     *   files for any other system are ignored. null = no restriction.
     */
    fun scan(folders: List<RomFolder>, existing: Map<String, Game>, allowed: Set<String>? = null, onProgress: (String) -> Unit = {}): Result {
        val found = LinkedHashMap<String, Game>()
        var unknown = 0
        for (folder in folders) {
            val root = File(folder.path)
            if (!root.isDirectory) continue
            val override = Platforms.find(folder.platformOverride)
            walk(root, root, override, depth = 0) { file, platform ->
                onProgress(file.name)
                if (platform == null) { unknown++; return@walk }
                if (allowed != null && platform.id !in allowed) return@walk
                val id = Ids.of(file.absolutePath)
                val prev = existing[id]
                val stem = if (file.isDirectory) dirTitle(file) else file.nameWithoutExtension
                found[id] = prev?.copy(
                    path = file.absolutePath,
                    title = if (prev.titleLocked) prev.title else prev.title,
                    hidden = prev.hidden,
                ) ?: Game(
                    id = id,
                    title = TitleCleaner.clean(stem),
                    platformId = platform.id,
                    kind = GameKind.ROM,
                    path = file.absolutePath,
                    fileSize = if (file.isFile) file.length() else 0,
                    meta = GameMeta(region = TitleCleaner.region(stem)),
                )
            }
        }
        return Result(found.values.toList(), unknown)
    }

    private fun dirTitle(dir: File): String {
        // PS3/PS4 dumps often carry the title in PARAM.SFO; fall back to folder name.
        val sfo = listOf("PS3_GAME/PARAM.SFO", "PARAM.SFO", "sce_sys/param.sfo").map { File(dir, it) }.firstOrNull { it.isFile }
        return sfo?.let { ParamSfo.title(it) } ?: dir.name
    }

    private fun walk(root: File, dir: File, inherited: Platform?, depth: Int, emit: (File, Platform?) -> Unit) {
        if (depth > 5) return
        val folderPlatform = inherited ?: Platforms.aliasMap[Platforms.normalize(dir.name)]
        val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        if (children.any { it.name == ".mfignore" }) return

        // Files referenced by .cue / .m3u / .gdi are part of another game.
        val referenced = HashSet<String>()
        children.filter { it.isFile && it.extension.lowercase() in setOf("cue", "m3u", "gdi") }.forEach { sheet ->
            runCatching {
                sheet.readLines().forEach { line ->
                    val name = Regex("\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        ?: line.trim().takeIf { sheet.extension.equals("m3u", true) && it.isNotEmpty() && !it.startsWith("#") }
                        ?: line.trim().split(Regex("\\s+")).getOrNull(4)?.takeIf { sheet.extension.equals("gdi", true) }
                    if (name != null) referenced += File(name).name.lowercase()
                }
            }
        }

        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (AddOns.isAddOnFolder(f.name)) continue
                val asDirGame = if (folderPlatform != null) {
                    folderPlatform.takeIf { p -> p.dirMarkers.any { File(f, it).exists() } }
                } else {
                    // Unlabelled folder: only unambiguous markers (PS3_GAME layout).
                    Platforms.get("ps3").takeIf { File(f, "PS3_GAME/USRDIR/EBOOT.BIN").exists() }
                }
                if (asDirGame != null) emit(f, asDirGame)
                else walk(root, f, folderPlatform, depth + 1, emit)
                continue
            }
            if (f.name.lowercase() in referenced) continue
            val ext = f.name.lowercase().let { n -> if (n.endsWith(".nkit.iso")) "nkit.iso" else f.extension.lowercase() }
            if (ext in ignoredExt) continue
            val platform = when {
                folderPlatform != null -> folderPlatform.takeIf { ext in it.extensions }
                else -> Platforms.uniqueExtensions[ext]
            }
            if (platform != null && AddOns.isAddOn(f.name, platform.id)) continue
            if (platform == null && ext !in quietExt) emit(f, null) else if (platform != null) emit(f, platform)
        }
    }

    private val ignoredExt = setOf("txt", "nfo", "jpg", "jpeg", "png", "srm", "sav", "state", "xml", "dat", "db", "ini", "cfg", "md", "pdf", "sfv", "ds_store")
    private val quietExt = setOf("bin", "img", "sub", "ccd", "raw", "wav", "mp3", "ogg", "lnk")
}

/** Updates, patches and DLC sit next to games but aren't games themselves (Switch especially). */
object AddOns {
    private val titleId = Regex("[\\[(]([0-9A-Fa-f]{16})[\\])]")
    private val tag = Regex("[\\[(](?:[^\\])]*\\b)?(update|upd|dlc|patch|add-?on|aoc)(?:\\b[^\\])]*)?[\\])]", RegexOption.IGNORE_CASE)
    private val version = Regex("[\\[(]v(\\d+)[\\])]", RegexOption.IGNORE_CASE)
    private val folders = setOf("update", "updates", "dlc", "dlcs", "patch", "patches", "addons", "add-ons", "aoc")

    fun isAddOnFolder(name: String) = name.lowercase() in folders

    // PS4 content packages: -A0100 is the shipped app version; anything higher is a patch.
    private val ps4Version = Regex("-A(\\d{4})-V\\d{4}", RegexOption.IGNORE_CASE)

    // "[Base + Update + DLC]", "(Game+Upd)": a merged dump, which is the game itself.
    private val bundle = Regex("[\\[(][^\\])]*(base|game|\\+)[^\\])]*[\\])]", RegexOption.IGNORE_CASE)

    fun isAddOn(fileName: String, platformId: String): Boolean {
        if (bundle.containsMatchIn(fileName)) return false
        if (tag.containsMatchIn(fileName)) return true
        // Switch 2 .nup packages can hold the game with its updates merged in, so its version
        // and title ID aren't evidence of an add-on.
        if (fileName.endsWith(".nup", ignoreCase = true)) return false
        when (platformId) {
            "switch", "switch2" -> {
                // Title IDs: base games end in 000, updates in 800, DLC in anything else.
                titleId.find(fileName)?.let { if (!it.groupValues[1].endsWith("000")) return true }
                // Update dumps carry their version as [v65536] and up; base games are [v0].
                version.find(fileName)?.groupValues?.get(1)?.toLongOrNull()?.let { if (it >= 65536) return true }
            }
            "ps4" -> {
                ps4Version.find(fileName)?.groupValues?.get(1)?.let { if (it != "0100") return true }
                if (Regex("[-_ ]patch\\b", RegexOption.IGNORE_CASE).containsMatchIn(fileName)) return true
            }
            "xbox360" -> if (Regex("^TU[_ \\d]", RegexOption.IGNORE_CASE).containsMatchIn(fileName) || Regex("\\bTU\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(fileName)) return true
        }
        return false
    }
}

object Hashing {
    fun crc32(file: File, maxBytes: Long = 256L * 1024 * 1024): String? {
        if (!file.isFile || file.length() > maxBytes) return null
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            // Skip iNES / SNES copier headers so hashes match No-Intro.
            val ext = file.extension.lowercase()
            if (ext in setOf("smc", "sfc") && file.length() % 1024 == 512L) input.skip(512)
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n) }
        }
        return "%08X".format(crc.value)
    }

    fun md5(file: File): String = MessageDigest.getInstance("MD5").let { md ->
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Minimal PARAM.SFO reader (title field). */
object ParamSfo {
    fun title(file: File): String? = runCatching {
        val b = file.readBytes()
        fun u32(o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        fun u16(o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
        if (u32(0) != 0x46535000) return null
        val keyTable = u32(8); val dataTable = u32(12); val count = u32(16)
        for (i in 0 until count) {
            val e = 20 + i * 16
            val keyOff = keyTable + u16(e)
            val key = String(b, keyOff, b.indexOfFirst(keyOff) - keyOff)
            if (key == "TITLE") {
                val len = u32(e + 4); val off = dataTable + u32(e + 12)
                return String(b, off, len).trimEnd('\u0000').trim()
            }
        }
        null
    }.getOrNull()

    private fun ByteArray.indexOfFirst(from: Int): Int { var i = from; while (i < size && this[i] != 0.toByte()) i++; return i }
}
