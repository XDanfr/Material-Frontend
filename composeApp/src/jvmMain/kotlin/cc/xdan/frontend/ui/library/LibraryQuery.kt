package cc.xdan.frontend.ui.library

import cc.xdan.frontend.data.TitleCleaner
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.SortOrder

/** Sidebar collections. */
sealed class Collection(val key: String, val label: String) {
    data object All : Collection("all", "All games")
    data object Recent : Collection("recent", "Recently played")
    data object Favorites : Collection("fav", "Favourites")
    data object Ports : Collection("ports", "Ports & recomps")
    data object Hidden : Collection("hidden", "Hidden")
    data class OfPlatform(val id: String) : Collection("p:$id", Platforms.get(id).name)
}

object LibraryQuery {
    fun filter(games: List<Game>, c: Collection, search: String): List<Game> {
        val base = when (c) {
            Collection.All -> games.filter { !it.hidden }
            Collection.Recent -> games.filter { !it.hidden && it.lastPlayed != null }
            Collection.Favorites -> games.filter { !it.hidden && it.favorite }
            Collection.Ports -> games.filter { !it.hidden && it.kind == GameKind.PORT }
            Collection.Hidden -> games.filter { it.hidden }
            is Collection.OfPlatform -> games.filter { !it.hidden && it.platformId == c.id }
        }
        if (search.isBlank()) return base
        val q = search.trim().lowercase()
        val qKey = TitleCleaner.key(q)
        return base.filter { g ->
            g.title.lowercase().contains(q) || TitleCleaner.key(g.title).contains(qKey) ||
                Platforms.get(g.platformId).shortName.lowercase() == q || g.meta.developer?.lowercase()?.contains(q) == true
        }
    }

    fun sort(games: List<Game>, order: SortOrder, descending: Boolean): List<Game> {
        val sorted = when (order) {
            SortOrder.TITLE -> games.sortedBy { it.sortKey }
            SortOrder.LAST_PLAYED -> games.sortedByDescending { it.lastPlayed ?: 0 }
            SortOrder.PLAY_TIME -> games.sortedByDescending { it.playTimeSeconds }
            SortOrder.ADDED -> games.sortedByDescending { it.addedAt }
            SortOrder.RELEASE -> games.sortedByDescending { it.meta.releaseYear ?: 0 }
            SortOrder.RATING -> games.sortedByDescending { it.meta.rating ?: -1f }
            SortOrder.PLATFORM -> games.sortedWith(compareBy({ Platforms.get(it.platformId).name }, { it.sortKey }))
        }
        return if (descending) sorted.reversed() else sorted
    }

    fun platformsIn(games: List<Game>): List<Pair<String, Int>> =
        games.filter { !it.hidden }.groupingBy { it.platformId }.eachCount().toList()
            .sortedWith(compareBy({ Platforms.all.indexOfFirst { p -> p.id == it.first }.let { i -> if (i < 0) 999 else i } }))
}
