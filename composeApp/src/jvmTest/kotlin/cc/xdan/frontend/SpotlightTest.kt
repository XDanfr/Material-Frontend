package cc.xdan.frontend

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.SpotlightMode
import cc.xdan.frontend.ui.bigscreen.buildShelves
import cc.xdan.frontend.ui.bigscreen.featuredGames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotlightTest {
    private val games = listOf(
        Game("a", "A", "snes", path = "/a", lastPlayed = 10),
        Game("b", "B", "snes", path = "/b", favorite = true),
        Game("c", "C", "snes", path = "/c", lastPlayed = 20),
        Game("u", "U", "steam", path = "1", installed = false, lastPlayed = 30, favorite = true),
    )

    @Test fun modes() {
        assertEquals(listOf("c", "a"), featuredGames(games, SpotlightMode.RECENT).map { it.id })
        assertEquals(listOf("b"), featuredGames(games, SpotlightMode.FAVOURITES).map { it.id })
        assertEquals(listOf("b", "a"), featuredGames(games, SpotlightMode.CHOSEN, listOf("b", "u", "a")).map { it.id })
        assertEquals(setOf("a", "b", "c"), featuredGames(games, SpotlightMode.RANDOM, seed = 7).map { it.id }.toSet())
        // Nothing chosen yet → falls back rather than showing an empty spotlight.
        assertTrue(featuredGames(games, SpotlightMode.CHOSEN).isNotEmpty())
    }

    @Test fun homeShelvesOnlyInstalled() {
        assertTrue(buildShelves(games).flatMap { it.games }.none { !it.installed })
    }
}
