package cc.xdan.frontend.scraper

import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.data.Hashing
import cc.xdan.frontend.data.TitleCleaner
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.GameMeta
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.ScraperSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

fun createHttpClient() = HttpClient(CIO) {
    install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000 }
    defaultRequest { header("User-Agent", "MaterialFrontend/0.1") }
    expectSuccess = false
}

/** What a provider found. Image fields are remote URLs. */
data class ScrapeResult(
    val title: String? = null,
    val meta: GameMeta = GameMeta(),
    val cover: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val screenshot: String? = null,
) {
    fun merge(o: ScrapeResult) = ScrapeResult(
        title ?: o.title,
        GameMeta(
            meta.description ?: o.meta.description, meta.developer ?: o.meta.developer, meta.publisher ?: o.meta.publisher,
            meta.releaseYear ?: o.meta.releaseYear, meta.genres.ifEmpty { o.meta.genres }, meta.players ?: o.meta.players,
            meta.rating ?: o.meta.rating, meta.region ?: o.meta.region,
        ),
        cover ?: o.cover, hero ?: o.hero, logo ?: o.logo, screenshot ?: o.screenshot,
    )
    val complete get() = cover != null && hero != null && logo != null && meta.description != null
}

interface ScraperProvider {
    val id: String
    val name: String
    fun configured(s: ScraperSettings): Boolean
    suspend fun scrape(game: Game, s: ScraperSettings): ScrapeResult?
}

private val json = Json { ignoreUnknownKeys = true }
private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
private fun JsonObject.obj(k: String) = this[k] as? JsonObject
private fun JsonObject.arr(k: String) = this[k] as? JsonArray

/** Free, keyless box art / title screens / snaps by No-Intro name. */
class LibretroThumbnails(private val http: HttpClient) : ScraperProvider {
    override val id = "libretro"
    override val name = "libretro-thumbnails"
    override fun configured(s: ScraperSettings) = true
    private val indexCache = ConcurrentHashMap<String, List<String>>()

    private fun safe(n: String) = n.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

    private suspend fun index(system: String, kind: String): List<String> = indexCache.getOrPut("$system/$kind") {
        runCatching {
            val html = http.get("https://thumbnails.libretro.com/${system.encodeURLPathPart()}/$kind/").bodyAsText()
            Regex("href=\"([^\"]+)\\.png\"").findAll(html).map { java.net.URLDecoder.decode(it.groupValues[1], "UTF-8") }.toList()
        }.getOrDefault(emptyList())
    }

    private suspend fun match(system: String, kind: String, game: Game): String? {
        val stem = File(game.path).nameWithoutExtension
        val names = index(system, kind)
        val exact = names.firstOrNull { it == safe(stem) }
        val key = TitleCleaner.key(game.title)
        val pick = exact ?: names.filter { TitleCleaner.key(it) == key }
            .sortedBy { n -> listOf("(USA", "(World", "(Europe", "(Japan").indexOfFirst { n.contains(it) }.let { if (it < 0) 9 else it } }
            .firstOrNull() ?: return null
        return "https://thumbnails.libretro.com/${system.encodeURLPathPart()}/$kind/${pick.encodeURLPathPart()}.png"
    }

    override suspend fun scrape(game: Game, s: ScraperSettings): ScrapeResult? {
        val system = Platforms.get(game.platformId).thumbnailsName ?: return null
        return ScrapeResult(
            cover = match(system, "Named_Boxarts", game),
            screenshot = match(system, "Named_Snaps", game) ?: match(system, "Named_Titles", game),
        ).takeIf { it.cover != null || it.screenshot != null }
    }
}

/** SteamGridDB: grids, heroes, logos. Great for Steam and modern/PC titles. */
class SteamGridDb(private val http: HttpClient) : ScraperProvider {
    override val id = "steamgriddb"
    override val name = "SteamGridDB"
    override fun configured(s: ScraperSettings) = s.steamGridDbKey.isNotBlank()
    private val base = "https://www.steamgriddb.com/api/v2"

    private suspend fun get(path: String, key: String): JsonObject? = runCatching {
        val r = http.get("$base$path") { header("Authorization", "Bearer $key") }
        if (!r.status.isSuccess()) null else json.parseToJsonElement(r.bodyAsText()).jsonObject
    }.getOrNull()

    private fun first(o: JsonObject?) = o?.arr("data")?.firstOrNull()?.jsonObject?.get("url").str()

    override suspend fun scrape(game: Game, s: ScraperSettings): ScrapeResult? {
        val key = s.steamGridDbKey
        val steamId = game.steamAppId
        val target = if (steamId != null) "steam/$steamId" else {
            val results = get("/search/autocomplete/${game.title.encodeURLPathPart()}", key)?.arr("data")?.map { it.jsonObject }.orEmpty()
            // Prefer an exact (normalised) title match over the first fuzzy hit.
            val k = TitleCleaner.key(game.title)
            val hit = results.firstOrNull { TitleCleaner.key(it["name"].str().orEmpty()) == k } ?: results.firstOrNull()
            val id = hit?.get("id")?.jsonPrimitive?.longOrNull ?: return null
            "game/$id"
        }
        return ScrapeResult(
            cover = first(get("/grids/$target?dimensions=600x900,660x930&types=static", key)),
            hero = if (s.downloadHeroes) first(get("/heroes/$target?types=static", key)) else null,
            logo = first(get("/logos/$target?types=static", key)),
        )
    }
}

/** IGDB (Twitch credentials): metadata + covers + artworks. */
class Igdb(private val http: HttpClient) : ScraperProvider {
    override val id = "igdb"
    override val name = "IGDB"
    override fun configured(s: ScraperSettings) = s.igdbClientId.isNotBlank() && s.igdbClientSecret.isNotBlank()
    @Volatile private var token: Pair<String, Long>? = null

    private suspend fun token(s: ScraperSettings): String? {
        token?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first }
        val r = runCatching {
            http.post("https://id.twitch.tv/oauth2/token") {
                parameter("client_id", s.igdbClientId); parameter("client_secret", s.igdbClientSecret); parameter("grant_type", "client_credentials")
            }
        }.getOrNull() ?: return null
        val o = runCatching { json.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrNull() ?: return null
        val t = o["access_token"].str() ?: return null
        val exp = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        token = t to System.currentTimeMillis() + exp * 1000 - 60_000
        return t
    }

    override suspend fun scrape(game: Game, s: ScraperSettings): ScrapeResult? {
        val t = token(s) ?: return null
        val platform = Platforms.get(game.platformId)
        val where = platform.igdbId?.takeIf { game.kind != GameKind.STEAM }?.let { " where platforms = ($it);" } ?: ""
        val q = "search \"${game.title.replace("\"", "")}\"; fields name,summary,first_release_date,genres.name,cover.image_id,artworks.image_id,screenshots.image_id,total_rating,involved_companies.company.name,involved_companies.developer,involved_companies.publisher;$where limit 1;"
        val r = runCatching {
            http.post("https://api.igdb.com/v4/games") {
                header("Client-ID", s.igdbClientId); header("Authorization", "Bearer $t"); setBody(q)
            }
        }.getOrNull() ?: return null
        val g = runCatching { json.parseToJsonElement(r.bodyAsText()).jsonArray.firstOrNull()?.jsonObject }.getOrNull() ?: return null
        fun img(id: String?, size: String) = id?.let { "https://images.igdb.com/igdb/image/upload/$size/$it.jpg" }
        val companies = g.arr("involved_companies")?.map { it.jsonObject }.orEmpty()
        fun company(role: String) = companies.firstOrNull { it[role]?.jsonPrimitive?.contentOrNull == "true" }?.obj("company")?.get("name").str()
        // Title is not taken from IGDB: text search can match a different game.
        return ScrapeResult(
            meta = GameMeta(
                description = g["summary"].str(),
                developer = company("developer"), publisher = company("publisher"),
                releaseYear = g["first_release_date"]?.jsonPrimitive?.longOrNull?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year },
                genres = g.arr("genres")?.mapNotNull { it.jsonObject["name"].str() }.orEmpty().take(3),
                rating = g["total_rating"]?.jsonPrimitive?.doubleOrNull?.let { (it / 100).toFloat() },
            ),
            cover = img(g.obj("cover")?.get("image_id").str(), "t_cover_big_2x"),
            hero = if (s.downloadHeroes) img(g.arr("artworks")?.firstOrNull()?.jsonObject?.get("image_id").str(), "t_1080p") else null,
            screenshot = img(g.arr("screenshots")?.firstOrNull()?.jsonObject?.get("image_id").str(), "t_screenshot_big"),
        )
    }
}

/** Steam store + CDN art for Steam games (no key). */
class SteamStore(private val http: HttpClient) : ScraperProvider {
    override val id = "steam"
    override val name = "Steam"
    override fun configured(s: ScraperSettings) = true

    override suspend fun scrape(game: Game, s: ScraperSettings): ScrapeResult? {
        val appId = game.steamAppId ?: return null
        val cdn = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId"
        val data = runCatching {
            json.parseToJsonElement(http.get("https://store.steampowered.com/api/appdetails?appids=$appId&l=english").bodyAsText())
                .jsonObject.obj(appId)?.obj("data")
        }.getOrNull()
        return ScrapeResult(
            meta = GameMeta(
                description = data?.get("short_description").str(),
                developer = data?.arr("developers")?.firstOrNull().str(),
                publisher = data?.arr("publishers")?.firstOrNull().str(),
                releaseYear = data?.obj("release_date")?.get("date").str()?.let { Regex("(19|20)\\d\\d").find(it)?.value?.toIntOrNull() },
                genres = data?.arr("genres")?.mapNotNull { it.jsonObject["description"].str() }.orEmpty().take(3),
                rating = data?.obj("metacritic")?.get("score")?.jsonPrimitive?.intOrNull?.let { it / 100f },
            ),
            cover = "$cdn/library_600x900_2x.jpg", hero = "$cdn/library_hero.jpg", logo = "$cdn/logo.png", screenshot = "$cdn/header.jpg",
        )
    }
}

/** Downloads remote images into the media cache. */
class AssetDownloader(private val http: HttpClient) {
    /** [fresh]: new file name, so a re-fetch never reuses (or is masked by the image cache of) the old file. */
    suspend fun fetch(url: String?, game: Game, kind: String, fresh: Boolean = false): String? {
        if (url == null) return null
        if (!url.startsWith("http")) return url
        val ext = url.substringAfterLast('.').substringBefore('?').lowercase().takeIf { it in setOf("png", "jpg", "jpeg", "webp") } ?: "png"
        val out = File(AppPaths.media, "${game.platformId}/${game.id}/$kind${if (fresh) "-" + System.currentTimeMillis() else ""}.$ext")
        if (out.isFile && out.length() > 0) return out.path
        return runCatching {
            val r = http.get(url)
            if (!r.status.isSuccess()) return null
            val bytes = r.bodyAsBytes()
            if (bytes.size < 200) return null
            out.parentFile.mkdirs(); out.writeBytes(bytes); out.path
        }.getOrNull()
    }
}
