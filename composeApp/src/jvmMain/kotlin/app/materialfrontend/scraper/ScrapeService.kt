package app.materialfrontend.scraper

import app.materialfrontend.data.LibraryStore
import app.materialfrontend.data.SettingsStore
import app.materialfrontend.model.Game
import app.materialfrontend.model.GameAssets
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ScrapeProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val current: String? = null)

/** Runs providers in priority order, merging results, downloading art, and updating the library. */
class ScrapeService(
    http: HttpClient,
    private val library: LibraryStore,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val providers: Map<String, ScraperProvider> = listOf(
        SteamGridDb(http), Igdb(http), LibretroThumbnails(http), SteamStore(http),
    ).associateBy { it.id }
    private val downloader = AssetDownloader(http)

    private val _progress = MutableStateFlow(ScrapeProgress())
    val progress: StateFlow<ScrapeProgress> = _progress.asStateFlow()
    private var job: Job? = null

    fun providerStatus(): List<Pair<String, Boolean>> = providers.values.map { it.name to it.configured(settings.value.scraper) }

    fun scrape(games: List<Game>, force: Boolean = false) {
        val targets = if (force) games else games.filter { !it.scraped }
        if (targets.isEmpty()) return
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            _progress.value = ScrapeProgress(true, 0, targets.size)
            val sem = Semaphore(4)
            var done = 0
            targets.map { g ->
                async {
                    sem.withPermit {
                        _progress.value = _progress.value.copy(current = g.title)
                        runCatching { scrapeOne(g) }.onFailure { System.err.println("Scrape ${g.title}: ${it.message}") }
                        synchronized(this@ScrapeService) { done++ }
                        _progress.value = _progress.value.copy(done = done)
                    }
                }
            }.awaitAll()
            _progress.value = ScrapeProgress(false, done, targets.size)
        }
    }

    fun cancel() { job?.cancel(); _progress.value = ScrapeProgress() }

    private suspend fun scrapeOne(game: Game) {
        val s = settings.value.scraper
        val order = (listOf("steam") + s.providerOrder + providers.keys).distinct()
        var result = ScrapeResult()
        for (id in order) {
            val p = providers[id] ?: continue
            if (!p.configured(s)) continue
            val r = runCatching { p.scrape(game, s) }.getOrNull() ?: continue
            result = result.merge(r)
            if (result.complete) break
        }
        val assets = GameAssets(
            cover = game.assets.cover ?: downloader.fetch(result.cover, game, "cover"),
            hero = game.assets.hero ?: downloader.fetch(result.hero, game, "hero"),
            logo = game.assets.logo ?: downloader.fetch(result.logo, game, "logo"),
            screenshot = game.assets.screenshot ?: downloader.fetch(result.screenshot, game, "screenshot"),
            icon = game.assets.icon,
        )
        library.updateGame(game.id) { g ->
            g.copy(
                title = if (g.titleLocked || result.title.isNullOrBlank()) g.title else result.title,
                meta = result.meta.copy(region = g.meta.region).let { m -> if (m.description == null && g.meta.description != null) g.meta else m },
                assets = assets,
                scraped = true,
            )
        }
    }
}
