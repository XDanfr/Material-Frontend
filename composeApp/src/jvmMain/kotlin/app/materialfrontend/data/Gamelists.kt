package app.materialfrontend.data

import app.materialfrontend.model.Game
import app.materialfrontend.model.RomFolder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Imports metadata and artwork from existing EmulationStation (ES-DE / classic) and Pegasus setups,
 * matched to scanned games by ROM path. Only fills what's missing, so scraping and user edits win.
 */
object Gamelists {
    data class Entry(
        val title: String? = null, val description: String? = null, val developer: String? = null, val publisher: String? = null,
        val year: Int? = null, val genres: List<String> = emptyList(), val players: String? = null, val rating: Float? = null,
        val favorite: Boolean = false, val hidden: Boolean = false, val playCount: Int = 0, val lastPlayed: Long? = null,
        val cover: String? = null, val hero: String? = null, val logo: String? = null, val screenshot: String? = null,
    )

    private val home = System.getProperty("user.home")

    fun apply(games: List<Game>, folders: List<RomFolder>): List<Game> {
        val entries = HashMap<String, Entry>()   // absolute rom path → entry
        systemDirs(folders).forEach { dir ->
            esGamelistFiles(dir).forEach { entries.putAll(readEs(it, dir)) }
            pegasusFiles(dir).forEach { entries.putAll(readPegasus(it)) }
        }
        if (entries.isEmpty()) return games
        return games.map { g -> entries[File(g.path).absolutePath]?.let { merge(g, it) } ?: g }
    }

    /** Root folders plus their direct sub-folders (one per system in most setups). */
    private fun systemDirs(folders: List<RomFolder>) = folders.map { File(it.path) }.filter { it.isDirectory }
        .flatMap { root -> listOf(root) + root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty() }

    private fun esGamelistFiles(romDir: File) = listOf(
        File(romDir, "gamelist.xml"),
        File(home, "ES-DE/gamelists/${romDir.name}/gamelist.xml"),
        File(home, ".emulationstation/gamelists/${romDir.name}/gamelist.xml"),
        File(home, "Emulation/tools/downloaded_media/../../ES-DE/gamelists/${romDir.name}/gamelist.xml"),
    ).filter { it.isFile }.distinctBy { it.canonicalPath }

    private fun pegasusFiles(romDir: File) = listOf("metadata.pegasus.txt", "metadata.txt").map { File(romDir, it) }.filter { it.isFile }

    private fun merge(g: Game, e: Entry): Game {
        val m = g.meta
        return g.copy(
            title = if (g.titleLocked || e.title.isNullOrBlank() || g.scraped) g.title else e.title,
            meta = m.copy(
                description = m.description ?: e.description, developer = m.developer ?: e.developer, publisher = m.publisher ?: e.publisher,
                releaseYear = m.releaseYear ?: e.year, genres = m.genres.ifEmpty { e.genres }, players = m.players ?: e.players, rating = m.rating ?: e.rating,
            ),
            assets = g.assets.copy(
                cover = g.assets.cover ?: e.cover, hero = g.assets.hero ?: e.hero, logo = g.assets.logo ?: e.logo, screenshot = g.assets.screenshot ?: e.screenshot,
            ),
            favorite = g.favorite || e.favorite, hidden = g.hidden || e.hidden,
            playCount = maxOf(g.playCount, e.playCount), lastPlayed = listOfNotNull(g.lastPlayed, e.lastPlayed).maxOrNull(),
            // Already has artwork → scraper doesn't need to redo it.
            scraped = g.scraped || (e.cover != null && e.description != null),
        )
    }

    // ---------------- EmulationStation gamelist.xml ----------------

    /** ES dates look like 19910821T000000. */
    private fun esDate(s: String?): Long? = s?.takeIf { it.length >= 8 }?.let {
        runCatching { java.time.LocalDate.of(it.substring(0, 4).toInt(), it.substring(4, 6).toInt(), it.substring(6, 8).toInt()).toEpochDay() * 86_400_000 }.getOrNull()
    }

    private fun resolve(base: File, p: String?): File? {
        if (p.isNullOrBlank()) return null
        val clean = p.replace("%ROMPATH%", base.parent ?: "").removePrefix("./")
        val f = File(clean).let { if (it.isAbsolute) it else File(base, clean) }
        return f.takeIf { it.exists() }
    }

    /** ES-DE keeps media outside the gamelist: downloaded_media/<system>/<kind>/<rom name>.(png|jpg). */
    private fun esdeMedia(system: String, romName: String, vararg kinds: String): String? {
        val roots = listOf(File(home, "ES-DE/downloaded_media"), File(home, ".emulationstation/downloaded_media"), File(home, "Emulation/tools/downloaded_media"))
        for (r in roots) for (k in kinds) for (ext in listOf("png", "jpg", "jpeg", "webp")) {
            File(r, "$system/$k/$romName.$ext").takeIf { it.isFile }?.let { return it.path }
        }
        return null
    }

    fun readEs(file: File, romDir: File): Map<String, Entry> = runCatching {
        val doc = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder().parse(file)
        val games = doc.getElementsByTagName("game")
        (0 until games.length).mapNotNull { i ->
            val e = games.item(i) as Element
            fun t(tag: String) = e.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val rom = resolve(romDir, t("path")) ?: return@mapNotNull null
            val stem = rom.nameWithoutExtension
            rom.absolutePath to Entry(
                title = t("name"), description = t("desc"), developer = t("developer"), publisher = t("publisher"),
                year = t("releasedate")?.take(4)?.toIntOrNull(), genres = t("genre")?.split(Regex("[,/]"))?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                players = t("players"), rating = t("rating")?.toFloatOrNull(),
                favorite = t("favorite") == "true", hidden = t("hidden") == "true",
                playCount = t("playcount")?.toIntOrNull() ?: 0, lastPlayed = esDate(t("lastplayed")),
                cover = resolve(romDir, t("image") ?: t("thumbnail"))?.path ?: esdeMedia(romDir.name, stem, "covers", "box2dfront"),
                hero = resolve(romDir, t("fanart"))?.path ?: esdeMedia(romDir.name, stem, "fanart"),
                logo = resolve(romDir, t("marquee") ?: t("wheel"))?.path ?: esdeMedia(romDir.name, stem, "marquees", "wheel"),
                screenshot = resolve(romDir, t("screenshot") ?: t("video")?.let { null })?.path ?: esdeMedia(romDir.name, stem, "screenshots", "titlescreens"),
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    // ---------------- Pegasus metadata ----------------

    /**
     * Pegasus format: `key: value` lines, indented continuation lines, entries start at `game:`
     * and list `file:`/`files:`. Assets: `assets.boxFront`, `assets.logo`, `assets.background`, `assets.screenshot`.
     * Also picks up Skraper-style `media/<rom name>/boxFront.png`.
     */
    fun readPegasus(file: File): Map<String, Entry> = runCatching {
        val dir = file.parentFile
        val blocks = ArrayList<MutableMap<String, MutableList<String>>>()
        var current: MutableMap<String, MutableList<String>>? = null
        var lastKey: String? = null
        file.readLines().forEach { raw ->
            if (raw.isBlank() || raw.trimStart().startsWith("#")) return@forEach
            if (raw.first().isWhitespace() && lastKey != null) { current?.get(lastKey)?.add(raw.trim()); return@forEach }
            val key = raw.substringBefore(':').trim().lowercase()
            val value = raw.substringAfter(':', "").trim()
            if (key == "game") { current = LinkedHashMap(); blocks += current!! }
            current?.getOrPut(key) { ArrayList() }?.apply { if (value.isNotEmpty()) add(value) }
            lastKey = key
        }
        val out = HashMap<String, Entry>()
        blocks.forEach { b ->
            fun v(k: String) = b[k]?.joinToString(" ")?.takeIf { it.isNotBlank() }
            fun asset(k: String) = v("assets.$k")?.let { File(dir, it.removePrefix("./")) }?.takeIf { it.isFile }?.path
            val files = (b["file"].orEmpty() + b["files"].orEmpty()).map { File(dir, it.removePrefix("./")) }
            files.forEach { rom ->
                fun media(name: String) = listOf("png", "jpg").map { File(dir, "media/${rom.nameWithoutExtension}/$name.$it") }.firstOrNull { it.isFile }?.path
                out[rom.absolutePath] = Entry(
                    title = v("game"), description = v("description") ?: v("summary"), developer = v("developer") ?: v("developers"),
                    publisher = v("publisher") ?: v("publishers"), year = v("release")?.take(4)?.toIntOrNull(),
                    genres = (v("genre") ?: v("genres"))?.split(',')?.map { it.trim() }.orEmpty(), players = v("players"),
                    rating = v("rating")?.removeSuffix("%")?.toFloatOrNull()?.div(100f),
                    cover = asset("boxfront") ?: asset("box_front") ?: media("boxFront"),
                    hero = asset("background") ?: asset("fanart") ?: media("background"),
                    logo = asset("logo") ?: asset("wheel") ?: media("logo"),
                    screenshot = asset("screenshot") ?: media("screenshot"),
                )
            }
        }
        out
    }.getOrDefault(emptyMap())
}
