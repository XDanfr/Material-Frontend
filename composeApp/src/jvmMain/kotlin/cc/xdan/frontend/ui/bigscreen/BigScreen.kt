package cc.xdan.frontend.ui.bigscreen

import cc.xdan.frontend.ui.components.PlacedLogo
import cc.xdan.frontend.ui.components.alignment
import cc.xdan.frontend.ui.components.rememberLogoPin
import cc.xdan.frontend.ui.components.rememberHeroArt
import cc.xdan.frontend.ui.components.heroBlur
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import cc.xdan.frontend.ui.settings.ControllerSettings
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.animateContentSize
import cc.xdan.frontend.emulators.launchLabel
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import cc.xdan.frontend.ui.components.PRESS_DELAY_MS
import cc.xdan.frontend.ui.components.LocalPressPulse
import cc.xdan.frontend.ui.components.pressBounce
import cc.xdan.frontend.ui.brand.Wordmark
import cc.xdan.frontend.ui.brand.BrandArtwork
import cc.xdan.frontend.ui.theme.FlexRecipes
import cc.xdan.frontend.ui.theme.reactive
import cc.xdan.frontend.ui.theme.flex
import cc.xdan.frontend.ui.brand.BrandLoader
import cc.xdan.frontend.ui.brand.BrandMark
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import cc.xdan.frontend.ui.library.Collection
import cc.xdan.frontend.ui.library.LibraryQuery
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.input.ControllerFamily
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.ThemeMode
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.components.ArtworkSeedEffect
import cc.xdan.frontend.ui.components.CoverArt
import cc.xdan.frontend.ui.components.HintBar
import cc.xdan.frontend.ui.components.ButtonGlyph
import cc.xdan.frontend.ui.components.focusEmphasis
import cc.xdan.frontend.ui.components.formatAgo
import cc.xdan.frontend.ui.components.formatPlayTime
import cc.xdan.frontend.ui.components.rememberImage
import cc.xdan.frontend.ui.theme.LocalApp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Shelf(val title: String, val games: List<Game>)

private enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library"), SETTINGS("Settings") }

/** One overlay at a time, top of the stack receives controller input. */
private sealed interface Overlay {
    data class Details(val game: Game, val focus: Int = 0) : Overlay
    data class LaunchWith(val game: Game, val focus: Int = 0) : Overlay
    data class Menu(val focus: Int = 0) : Overlay
    /** Hide / rename / remove for one game. */
    data class Manage(val game: Game, val focus: Int = 0, val armedRemove: Boolean = false) : Overlay
    data class Rename(val game: Game) : Overlay
    data object Search : Overlay
    /** Move the logo between the 3×3 presets with the D-pad; LB/RB size it from its anchor. */
    data class LogoEditor(val game: Game, val pin: cc.xdan.frontend.model.LogoPin, val width: Float) : Overlay
    /** Exit, switch mode, fullscreen, sleep/restart/shut down; [confirming] asks before restart/shut down. */
    data class Power(val focus: Int = 0, val confirming: cc.xdan.frontend.AppController.Power? = null) : Overlay
    /** "This title isn't installed. Add anyway?" */
    data class ConfirmSpotlight(val game: Game, val message: String, val focus: Int = 0) : Overlay
}

fun buildShelves(games: List<Game>): List<Shelf> {
    val visible = games.filter { !it.hidden && it.installed }  // Home is what you can play now; Library shows everything
    val out = ArrayList<Shelf>()
    visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }.take(15).takeIf { it.isNotEmpty() }?.let { out += Shelf("Continue playing", it) }
    visible.filter { it.favorite }.sortedBy { it.sortKey }.takeIf { it.isNotEmpty() }?.let { out += Shelf("Favourites", it) }
    visible.filter { it.installed }.sortedByDescending { it.addedAt }.take(15).takeIf { it.isNotEmpty() }?.let { out += Shelf("Recently added", it) }
    visible.filter { it.kind == GameKind.PORT }.takeIf { it.isNotEmpty() }?.let { out += Shelf("Ports & recomps", it) }
    visible.groupBy { it.platformId }.toList().sortedByDescending { it.second.size }.forEach { (pid, list) ->
        out += Shelf(Platforms.get(pid).name, list.sortedBy { it.sortKey })
    }
    return out
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BigScreen() {
    val app = LocalApp.current
    val games by app.library.state.collectAsState()
    val family by app.controllers.activeFamily.collectAsState()
    val settings by app.settings.state.collectAsState()

    val shelves = remember(games) { buildShelves(games) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    // Home: row -1 is the spotlight carousel; Up from the top of any tab focuses the tab bar.
    val spotSeed = remember { System.nanoTime() }  // Random: new picks each launch, stable while open
    val featured = remember(games, settings.spotlight, settings.spotlightGames, settings.spotlightHidden) { featuredGames(games, settings.spotlight, settings.spotlightGames, spotSeed, settings.spotlightHidden) }
    var row by remember { mutableIntStateOf(-1) }
    var spotIdx by remember { mutableIntStateOf(0) }
    var barFocus by remember { mutableStateOf(false) }
    var powerFocus by remember { mutableStateOf(false) }  // the power button at the end of the tab bar
    if (!barFocus) powerFocus = false
    if (row == -1 && featured.isEmpty()) row = 0
    // Shelves can shrink (a game installed/removed, favourites changed): keep focus on a real tile.
    if (row >= shelves.size) row = if (shelves.isEmpty()) -1 else shelves.lastIndex
    val cols = remember { mutableStateMapOf<Int, Int>() }
    // Library tab state
    val platforms = remember(games) {
        listOf<String?>(null) + games.filter { !it.hidden }.map { it.platformId }.distinct().sortedBy { Platforms.all.indexOfFirst { p -> p.id == it } } +
            listOfNotNull(HIDDEN.takeIf { games.any { it.hidden } })
    }
    var platformIdx by remember { mutableIntStateOf(0) }
    var gridIdx by remember { mutableIntStateOf(0) }
    var gridCols by remember { mutableIntStateOf(6) }
    val libGames = remember(games, platformIdx) {
        val pid = platforms.getOrNull(platformIdx)
        (if (pid == HIDDEN) games.filter { it.hidden } else games.filter { !it.hidden && (pid == null || it.platformId == pid) }).sortedBy { it.sortKey }
    }
    val overlays = remember { mutableStateListOf<Overlay>() }
    var searchResultsFocus by remember { mutableStateOf<Game?>(null) }

    val focused: Game? = when (tab) {
        Tab.HOME -> if (row == -1) featured.getOrNull(spotIdx) else shelves.getOrNull(row)?.games?.let { it.getOrNull((cols[row] ?: 0).coerceIn(0, (it.size - 1).coerceAtLeast(0))) }
        Tab.LIBRARY -> libGames.getOrNull(gridIdx)
        Tab.SETTINGS -> null
    }
    ArtworkSeedEffect(focused?.assets?.cover)

    val menuItems = remember(settings) {
        listOf(
            "Library view" to { app.mode.value = UiMode.LIST; app.fullscreen.value = false },
            "XMB view" to { app.mode.value = UiMode.XMB },
            "Rescan library" to { app.rescan() },
            "Fetch missing artwork" to { app.scraper.scrape(app.library.value) },
            "Theme: ${settings.themeMode.name.lowercase()}" to {
                app.updateSettings { it.copy(themeMode = ThemeMode.entries[(it.themeMode.ordinal + 1) % ThemeMode.entries.size]) }
            },
            "Confirm button: ${if (settings.nintendoConfirm) "right (Nintendo)" else "bottom"}" to { app.updateSettings { it.copy(nintendoConfirm = !it.nintendoConfirm) } },
            "Toggle fullscreen" to { app.fullscreen.value = !app.fullscreen.value },
        )
    }

    // Settings → Your name opens the on-screen keyboard (reusing rename, keyed by a placeholder id).
    val wantsName by app.nameEntry.collectAsState()
    LaunchedEffect(wantsName) {
        if (wantsName) { app.nameEntry.value = false; overlays += Overlay.Rename(Game(NAME_ID, settings.userName, "", path = "")) }
    }
    val currentFocused by rememberUpdatedState(focused)
    val libNow by rememberUpdatedState(libGames)
    val shelvesNow by rememberUpdatedState(shelves)
    val featuredNow by rememberUpdatedState(featured)
    val platformsNow by rememberUpdatedState(platforms)
    var pressPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            val top = overlays.lastOrNull()
            // Confirm: bounce the focused control, and let it play before the action changes the screen.
            if (e.nav == Nav.CONFIRM && top != Overlay.Search) { pressPulse++; delay(PRESS_DELAY_MS) }
            when (top) {
                is Overlay.Details -> {
                    val actions = 4
                    when (e.nav) {
                        Nav.LEFT -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0))
                        Nav.RIGHT -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(actions - 1))
                        Nav.CONFIRM -> when (top.focus) {
                            0 -> { overlays.clear(); app.play(top.game) }
                            1 -> { app.toggleFavorite(top.game.id); overlays[overlays.lastIndex] = top.copy(game = top.game.copy(favorite = !top.game.favorite)) }
                            2 -> overlays += Overlay.LaunchWith(top.game)
                            3 -> overlays += Overlay.Manage(games.firstOrNull { it.id == top.game.id } ?: top.game)
                        }
                        Nav.ALT -> { app.toggleFavorite(top.game.id); overlays[overlays.lastIndex] = top.copy(game = top.game.copy(favorite = !top.game.favorite)) }
                        Nav.BACK -> overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.LaunchWith -> {
                    val opts = Launchers.options(top.game, app.settings.value)
                    when (e.nav) {
                        Nav.UP -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0))
                        Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(opts.lastIndex.coerceAtLeast(0)))
                        Nav.CONFIRM -> opts.getOrNull(top.focus)?.let { (k, _) -> overlays.clear(); app.play(top.game, k) }
                        Nav.BACK -> overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.Menu -> when (e.nav) {
                    Nav.UP -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0))
                    Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(menuItems.lastIndex))
                    Nav.CONFIRM -> { menuItems[top.focus].second(); if (top.focus < 2) overlays.clear() }
                    Nav.BACK, Nav.MENU, Nav.GUIDE -> overlays.removeLast()
                    else -> {}
                }
                is Overlay.Manage -> {
                    val n = manageItems(top.game, settings).size
                    when (e.nav) {
                        Nav.UP -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0), armedRemove = false)
                        Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(n - 1), armedRemove = false)
                        Nav.CONFIRM -> manageAction(top, app, overlays)
                        Nav.BACK, Nav.MENU -> overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.Rename, Overlay.Search -> { /* handled by their own overlay */ }
                is Overlay.LogoEditor -> {
                    fun upd(o: Overlay.LogoEditor) { overlays[overlays.lastIndex] = o }
                    when (e.nav) {
                        Nav.UP -> upd(top.copy(pin = top.pin.moved(-1, 0)))
                        Nav.DOWN -> upd(top.copy(pin = top.pin.moved(1, 0)))
                        Nav.LEFT -> upd(top.copy(pin = top.pin.moved(0, -1)))
                        Nav.RIGHT -> upd(top.copy(pin = top.pin.moved(0, 1)))
                        Nav.PREV_TAB, Nav.PREV_PAGE -> upd(top.copy(width = (top.width - 0.04f).coerceAtLeast(0.12f)))
                        Nav.NEXT_TAB, Nav.NEXT_PAGE -> upd(top.copy(width = (top.width + 0.04f).coerceAtMost(0.9f)))
                        Nav.CONFIRM -> { app.setLogo(top.game.id, top.pin, top.width); closeLogoEditor(overlays, top.game.copy(logoPin = top.pin, logoWidth = top.width)) }
                        Nav.ALT -> { app.setLogo(top.game.id, null, null); closeLogoEditor(overlays, top.game.copy(logoPin = null, logoWidth = null)) }
                        Nav.BACK -> overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.Power -> {
                    val n = if (top.confirming != null) 2 else app.powerOptions().size
                    when (e.nav) {
                        Nav.UP -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0))
                        Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(n - 1))
                        Nav.CONFIRM -> powerAction(top, top.focus, app, overlays)
                        Nav.BACK -> if (top.confirming != null) overlays[overlays.lastIndex] = Overlay.Power() else overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.ConfirmSpotlight -> when (e.nav) {
                    Nav.UP, Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = 1 - top.focus)
                    Nav.CONFIRM -> { if (top.focus == 0) app.toggleSpotlight(top.game.id); overlays.removeLast(); overlays.lastOrNull()?.let { if (it is Overlay.Manage) overlays.removeLast() } }
                    Nav.BACK -> overlays.removeLast()
                    else -> {}
                }
                null -> if (barFocus) when (e.nav) {
                    Nav.LEFT -> if (powerFocus) powerFocus = false else tab = Tab.entries[(tab.ordinal + Tab.entries.size - 1) % Tab.entries.size]
                    Nav.RIGHT -> if (tab == Tab.entries.last()) powerFocus = true else tab = Tab.entries[tab.ordinal + 1]
                    Nav.PREV_TAB -> { powerFocus = false; tab = Tab.entries[(tab.ordinal + Tab.entries.size - 1) % Tab.entries.size] }
                    Nav.NEXT_TAB -> { powerFocus = false; tab = Tab.entries[(tab.ordinal + 1) % Tab.entries.size] }
                    Nav.CONFIRM -> if (powerFocus) overlays += Overlay.Power() else barFocus = false
                    Nav.DOWN, Nav.BACK -> barFocus = false
                    Nav.MENU, Nav.GUIDE -> overlays += Overlay.Menu()
                    Nav.SEARCH -> overlays += Overlay.Search
                    else -> {}
                } else when (e.nav) {
                    Nav.PREV_TAB -> tab = Tab.entries[(tab.ordinal + Tab.entries.size - 1) % Tab.entries.size]
                    Nav.NEXT_TAB -> tab = Tab.entries[(tab.ordinal + 1) % Tab.entries.size]
                    Nav.MENU, Nav.GUIDE -> overlays += Overlay.Menu()
                    Nav.SEARCH -> overlays += Overlay.Search
                    Nav.BACK -> if (tab != Tab.HOME) tab = Tab.HOME
                    Nav.CONFIRM -> if (tab != Tab.SETTINGS) currentFocused?.let { overlays += Overlay.Details(it) }
                    Nav.ALT -> if (tab != Tab.SETTINGS) currentFocused?.let { app.toggleFavorite(it.id) }
                    else -> if (tab == Tab.SETTINGS) { /* ControllerSettings handles its own navigation */ } else if (tab == Tab.HOME) {
                        val top0 = if (featuredNow.isEmpty()) 0 else -1
                        if (row == -1) when (e.nav) {
                            Nav.UP -> barFocus = true
                            Nav.DOWN -> if (shelvesNow.isNotEmpty()) row = 0
                            Nav.LEFT -> spotIdx = (spotIdx - 1).coerceAtLeast(0)
                            Nav.RIGHT -> spotIdx = (spotIdx + 1).coerceAtMost(featuredNow.lastIndex)
                            else -> {}
                        } else when (e.nav) {
                            Nav.UP -> if (row == top0) barFocus = true else row = (row - 1).coerceAtLeast(top0)
                            Nav.DOWN -> row = (row + 1).coerceAtMost((shelvesNow.size - 1).coerceAtLeast(0))
                            Nav.LEFT -> cols[row] = ((cols[row] ?: 0) - 1).coerceAtLeast(0)
                            Nav.RIGHT -> cols[row] = ((cols[row] ?: 0) + 1).coerceAtMost((shelvesNow.getOrNull(row)?.games?.size ?: 1) - 1)
                            Nav.PREV_PAGE -> cols[row] = 0
                            Nav.NEXT_PAGE -> cols[row] = (shelvesNow.getOrNull(row)?.games?.size ?: 1) - 1
                            else -> {}
                        }
                    } else {
                        when (e.nav) {
                            Nav.LEFT -> gridIdx = (gridIdx - 1).coerceAtLeast(0)
                            Nav.RIGHT -> gridIdx = (gridIdx + 1).coerceAtMost(libNow.lastIndex.coerceAtLeast(0))
                            Nav.UP -> if (gridIdx < gridCols) barFocus = true else gridIdx = (gridIdx - gridCols).coerceAtLeast(0)
                            Nav.DOWN -> gridIdx = (gridIdx + gridCols).coerceAtMost(libNow.lastIndex.coerceAtLeast(0))
                            Nav.PREV_PAGE -> { platformIdx = (platformIdx - 1 + platformsNow.size) % platformsNow.size; gridIdx = 0 }
                            Nav.NEXT_PAGE -> { platformIdx = (platformIdx + 1) % platformsNow.size; gridIdx = 0 }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalPressPulse provides pressPulse) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Backdrop(focused)
        Column(Modifier.fillMaxSize()) {
            TopBar(tab, family, barFocus && !powerFocus, powerFocus, onPower = { overlays += Overlay.Power() }) { tab = it; barFocus = false }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it / 10 * (if (targetState > initialState) 1 else -1) })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            }) { t ->
                when (t) {
                    Tab.HOME -> HomeTab(shelves, row, cols, focused, featured, spotIdx, !barFocus,
                        onSpot = { i -> if (row == -1 && spotIdx == i) overlays += Overlay.Details(featured[i]) else { row = -1; spotIdx = i } },
                        onClick = { r, c -> if (row == r && (cols[r] ?: 0) == c) shelves[r].games.getOrNull(c)?.let { overlays += Overlay.Details(it) } else { row = r; cols[r] = c } })
                    Tab.SETTINGS -> ControllerSettings(UiMode.BIGSCREEN, active = overlays.isEmpty() && !barFocus, modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
                        onExitTop = { barFocus = true })
                    Tab.LIBRARY -> LibraryTab(platforms, platformIdx, libGames, gridIdx, !barFocus, onCols = { gridCols = it }, onClick = { i -> if (gridIdx == i) overlays += Overlay.Details(libGames[i]) else gridIdx = i })
                }
            }
        }
        if (settings.bigScreenHints) HintBar(
            when (overlays.lastOrNull()) {
                is Overlay.Details -> listOf(Pad.SOUTH to "Select", Pad.WEST to "Favourite", Pad.EAST to "Back")
                is Overlay.Manage -> listOf(Pad.SOUTH to "Select", Pad.EAST to "Back")
                is Overlay.Rename -> listOf(Pad.SOUTH to "Type", Pad.WEST to "Delete", Pad.START to "Save", Pad.EAST to "Cancel")
                is Overlay.LogoEditor -> listOf(Pad.LEFT to "Move", Pad.LB to "", Pad.RB to "Size", Pad.SOUTH to "Save", Pad.WEST to "Reset to default", Pad.EAST to "Cancel")
                is Overlay.Menu, is Overlay.LaunchWith, is Overlay.ConfirmSpotlight, is Overlay.Power -> listOf(Pad.SOUTH to "Select", Pad.EAST to "Close")
                Overlay.Search -> listOf(Pad.SOUTH to "Select", Pad.WEST to "Delete", Pad.START to "Results", Pad.EAST to "Back")
                null -> if (powerFocus) listOf(Pad.SOUTH to "Power options", Pad.LEFT to "Tabs")
                        else if (barFocus) listOf(Pad.LEFT to "Switch tab", Pad.DOWN to "Back to content")
                        else if (tab == Tab.SETTINGS) listOf(Pad.SOUTH to "Change", Pad.LEFT to "Value", Pad.LB to "", Pad.RB to "Tabs", Pad.EAST to "Home")
                        else listOf(Pad.SOUTH to "Details", Pad.WEST to "Favourite", Pad.NORTH to "Search", Pad.LB to "", Pad.RB to "Tabs", Pad.START to "Menu")
            }.map { (p, l) -> (if (settings.nintendoConfirm && p == Pad.SOUTH) Pad.EAST else if (settings.nintendoConfirm && p == Pad.EAST) Pad.SOUTH else p) to l },
            family, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )

        // Overlays
        overlays.forEach { o ->
            when (o) {
                is Overlay.Details -> DetailsOverlay(games.firstOrNull { it.id == o.game.id } ?: o.game, o.focus,
                    onAction = { i -> overlays[overlays.indexOf(o)] = o.copy(focus = i) }, onBack = { overlays.remove(o) },
                    onPlay = { overlays.clear(); app.play(o.game) }, onLaunchWith = { overlays += Overlay.LaunchWith(o.game) },
                    onMore = { overlays += Overlay.Manage(games.firstOrNull { it.id == o.game.id } ?: o.game) })
                is Overlay.LaunchWith -> LaunchWithOverlay(o.game, o.focus) { k -> overlays.clear(); app.play(o.game, k) }
                is Overlay.Menu -> MenuOverlay(menuItems.map { it.first }, o.focus) { i -> menuItems[i].second(); overlays.clear() }
                is Overlay.Manage -> MenuOverlay(manageItems(o.game, settings).map { if (it == "Remove from library" && o.armedRemove) "Press again to remove" else it }, o.focus, title = o.game.title) { i ->
                    overlays[overlays.indexOf(o)] = o.copy(focus = i); manageAction(o.copy(focus = i), app, overlays)
                }
                is Overlay.ConfirmSpotlight -> MenuOverlay(listOf("Add anyway", "Cancel"), o.focus, title = o.message) { i ->
                    if (i == 0) app.toggleSpotlight(o.game.id); overlays.remove(o); overlays.lastOrNull()?.let { if (it is Overlay.Manage) overlays.remove(it) }
                }
                is Overlay.Power -> MenuOverlay(if (o.confirming != null) listOf(o.confirming.label, "Cancel") else app.powerOptions().map { it.label },
                    o.focus, title = o.confirming?.confirm ?: "Power") { i -> powerAction(o, i, app, overlays) }
                is Overlay.LogoEditor -> LogoEditorOverlay(o)
                is Overlay.Rename -> RenameOverlay(o.game, onDone = { t ->
                    if (t == null) { /* cancelled */ }
                    else if (o.game.id == NAME_ID) app.updateSettings { it.copy(userName = t.trim().take(32)) }
                    else if (t.isNotBlank()) app.rename(o.game.id, t.trim())
                    overlays.remove(o) })
                Overlay.Search -> SearchOverlay(games.filter { !it.hidden }, onClose = { overlays.remove(Overlay.Search) }) { g -> overlays.remove(Overlay.Search); overlays += Overlay.Details(g) }
            }
        }
    }
    }
}

@Composable
private fun Backdrop(game: Game?) {
    Crossfade(game?.assets?.hero ?: game?.assets?.screenshot ?: game?.assets?.cover, animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) { path ->
        val img by rememberImage(path, 1600)
        Box(Modifier.fillMaxSize()) {
            img?.let { Image(it, null, Modifier.fillMaxSize().alpha(0.55f).let { m -> if (path == game?.assets?.cover) m.blur(40.dp) else m }, contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), 0.55f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), 1f to MaterialTheme.colorScheme.surface)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), 0.6f to Color.Transparent)))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(tab: Tab, family: ControllerFamily, barFocus: Boolean, powerFocus: Boolean, onPower: () -> Unit, onTab: (Tab) -> Unit) {
    val app = LocalApp.current
    val pads by app.controllers.controllers.collectAsState()
    val time by produceState(LocalTime.now()) { while (true) { value = LocalTime.now(); delay(1000) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(Modifier.size(64.dp))
            BrandArtwork(Wordmark.TEXT, Modifier.height(26.dp).padding(end = 16.dp))
            Tab.entries.forEach { t ->
                val sel = t == tab
                val bg by animateFloatAsState(if (sel) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
                Text(t.label, Modifier.pressBounce(LocalPressPulse.current, sel && barFocus).focusEmphasis(sel && barFocus, 50.dp, 50.dp, 1.06f).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = bg)).clickable { onTab(t) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                    // Selected tab widens and rounds; others sit condensed.
                    style = MaterialTheme.typography.titleLarge.flex { copy(wdth = 92f) }.reactive(sel) { copy(wght = 760f, wdth = 114f, rond = 100f) },
                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pads.isEmpty()) Icon(Icons.Outlined.Keyboard, "Keyboard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            pads.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Gamepad, p.typeLabel, tint = MaterialTheme.colorScheme.primary)
                    p.battery?.let { Text(" $it%", style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (LocalApp.current.settings.state.collectAsState().value.bigScreenClock) Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.titleLarge.flex { copy(wdth = 76f, wght = 560f, rond = 60f) })
            Box(Modifier.size(48.dp).pressBounce(LocalPressPulse.current, powerFocus).focusEmphasis(powerFocus, 24.dp, 24.dp, 1.1f).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (powerFocus) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable(onClick = onPower), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PowerSettingsNew, "Power", tint = if (powerFocus) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Greeting(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val s by app.settings.state.collectAsState()
    // New line each hour; stable within it.
    val hour = java.time.LocalTime.now().hour
    val seed = remember(hour) { System.currentTimeMillis() / 3_600_000 }
    val text = Greetings.line(Greetings.forHour(hour, seed), s.userName.ifBlank { app.steamName ?: "" })
    Text(text, modifier, style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HomeTab(shelves: List<Shelf>, row: Int, cols: Map<Int, Int>, focused: Game?, featured: List<Game>, spotIdx: Int, contentFocus: Boolean,
                    onSpot: (Int) -> Unit, onClick: (Int, Int) -> Unit) {
    if (shelves.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandLoader(Modifier.size(180.dp), intervalMillis = 2600)
                Text("Your library is empty — add folders from the Library view.", style = MaterialTheme.typography.headlineSmall)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Hero info for the focused game
        Box(Modifier.fillMaxWidth().weight(0.42f).padding(horizontal = 56.dp), contentAlignment = Alignment.BottomStart) {
            val motion = MaterialTheme.motionScheme
            // The top area is the spotlight while it has focus, and describes the focused game otherwise.
            AnimatedContent(if (row == -1 || !contentFocus) null else focused, transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            }, contentKey = { it?.id ?: "spotlight" }) { g ->
                if (g != null) HeroInfo(g)
                else Column {
                    Greeting(Modifier.padding(top = 4.dp, bottom = 4.dp))
                    if (featured.isNotEmpty()) Box(Modifier.weight(1f)) { Spotlight(featured, spotIdx, row == -1 && contentFocus, onSpot) }
                }
            }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(row) { listState.animateScrollToItem(row.coerceAtLeast(0)) }
        LazyColumn(Modifier.fillMaxWidth().weight(0.58f), state = listState, contentPadding = PaddingValues(bottom = 120.dp), userScrollEnabled = true) {
            itemsIndexed(shelves) { r, shelf ->
                val active = r == row
                Column(Modifier.padding(vertical = 8.dp).alpha(if (active || row == -1) 1f else 0.7f)) {
                    Text(shelf.title, Modifier.padding(start = 56.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.titleLarge.flex { copy(wdth = 90f, wght = 430f) }.reactive(active) { copy(wght = 720f, wdth = 108f, rond = 100f) })
                    val rowState = rememberLazyListState()
                    val col = cols[r] ?: 0
                    LaunchedEffect(col) { rowState.animateScrollToItem((col - 1).coerceAtLeast(0)) }
                    LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        itemsIndexed(shelf.games, key = { _, g -> g.id }) { c, g ->
                            val f = active && c == col
                            ShelfCard(g, active, f) { onClick(r, c) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A shelf tile. The focused one widens from box art to a 16:9 card with the game's wide art and logo
 * (no wide art → its box art, blurred, behind the logo); moving on shrinks it back as the next one opens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfCard(g: Game, rowActive: Boolean, focused: Boolean, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val (wideArt, wideNeedsBlur) = rememberHeroArt(g)
    val expand = focused
    val h = if (rowActive) 252.dp else 198.dp
    val w by androidx.compose.animation.core.animateDpAsState(if (expand) h * 16f / 9f else h * 2f / 3f, motion.defaultSpatialSpec())
    val wideAlpha by animateFloatAsState(if (expand) 1f else 0f, motion.defaultEffectsSpec())
    Box(Modifier.width(w).height(h).pressBounce(LocalPressPulse.current, focused)
        .focusEmphasis(focused, baseCorner = 16.dp, focusedCorner = 28.dp, scale = if (expand) 1.02f else 1.1f)
        .clickable(onClick = onClick)) {
        // Box art stays underneath (cropped) so the change never shows an empty frame.
        // The wide layer is always in the tree (only its images/alpha change): adding and removing
        // nodes inside a scrolling row while it resizes trips a Compose layout crash.
        CoverArt(g, Modifier.fillMaxSize())
        val loadWide = expand || wideAlpha > 0f
        val hero by rememberImage(if (loadWide) wideArt ?: g.assets.cover else null, if (wideArt != null) 1280 else 400)
        val logo by rememberImage(if (loadWide) g.assets.logo else null, 600)
        val clear = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
        Box(Modifier.fillMaxSize().alpha(wideAlpha).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (loadWide) 1f else 0f))) {
            Image(hero?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) } ?: clear, g.title,
                Modifier.fillMaxSize().heroBlur(wideArt == null || wideNeedsBlur), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            PlacedLogo(logo, g.title, rememberLogoPin(g), g.effectiveLogoWidth(), Modifier.padding(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroInfo(g: Game) {
    val logo by rememberImage(g.assets.logo, 800)
    Column(Modifier.widthIn(max = 720.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val l = logo
        if (l != null) Image(l, g.title, Modifier.heightIn(max = 120.dp).widthIn(max = 480.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
        else {
            // The title "inflates" as a game gains focus: thin & condensed → heavy, wide and round.
            var entered by remember(g.id) { mutableStateOf(false) }
            LaunchedEffect(g.id) { entered = true }
            Text(g.title, style = MaterialTheme.typography.displayMedium.flex { copy(wght = 260f, wdth = 82f, rond = 0f, opsz = 144f) }
                .reactive(entered) { copy(wght = 760f, wdth = 104f, rond = 100f) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(listOfNotNull(Platforms.get(g.platformId).name, g.meta.releaseYear?.toString(), g.meta.genres.firstOrNull(), formatPlayTime(g.playTimeSeconds).takeIf { g.playTimeSeconds > 0 })
            .joinToString("  ·  "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        g.meta.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryTab(platforms: List<String?>, platformIdx: Int, games: List<Game>, index: Int, active: Boolean, onCols: (Int) -> Unit, onClick: (Int) -> Unit) {
    val family by LocalApp.current.controllers.activeFamily.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            ButtonGlyph(Pad.LT, family)
            Text(platforms[platformIdx]?.let { if (it == HIDDEN) "Hidden games" else Platforms.get(it).name } ?: "All systems", style = MaterialTheme.typography.headlineMediumEmphasized)
            ButtonGlyph(Pad.RT, family)
            Text("${games.size} games", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val n = (maxWidth / 180.dp).toInt().coerceAtLeast(3)
            LaunchedEffect(n) { onCols(n) }
            val state = rememberLazyGridState()
            LaunchedEffect(index) { state.animateScrollToItem(index) }
            LazyVerticalGrid(GridCells.Fixed(n), state = state, contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                itemsIndexed(games, key = { _, g -> g.id }) { i, g ->
                    Box(Modifier.aspectRatio(2f / 3f).pressBounce(LocalPressPulse.current, active && i == index).focusEmphasis(active && i == index, 16.dp, 28.dp, 1.08f).clickable { onClick(i) }) { CoverArt(g, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailsOverlay(g: Game, focus: Int, onAction: (Int) -> Unit, onBack: () -> Unit, onPlay: () -> Unit, onLaunchWith: () -> Unit, onMore: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val (heroPath, heroNeedsBlur) = rememberHeroArt(g)
    val hero by rememberImage(heroPath, 1920)
    val logo by rememberImage(g.assets.logo, 800)
    val target = remember(g, settings) { Launchers.resolve(g, settings) }
    AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.96f)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            hero?.let { Image(it, null, Modifier.fillMaxWidth().fillMaxHeight(0.62f).heroBlur(heroNeedsBlur), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 0.62f to MaterialTheme.colorScheme.surface)))
            Row(Modifier.fillMaxSize().padding(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                CoverArt(g, Modifier.width(260.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(28.dp)), maxDim = 600)
                Column(Modifier.weight(1f).padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val l = logo
                    if (l != null) Image(l, g.title, Modifier.heightIn(max = 140.dp).widthIn(max = 560.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
                    else Text(g.title, style = MaterialTheme.typography.displayMediumEmphasized)
                    Text(listOfNotNull(Platforms.get(g.platformId).name, g.meta.releaseYear?.toString(), g.meta.developer, g.lastPlayed?.let { "Played ${formatAgo(it).lowercase()}" } ?: "Never played", formatPlayTime(g.playTimeSeconds).takeIf { g.playTimeSeconds > 0 }).joinToString("  ·  "),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton(if (g.installed) "Play" else "Install", focus == 0, primary = true) { onAction(0); onPlay() }
                        BigButton("Favourite", focus == 1, suffix = "d", showSuffix = g.favorite, glyph = Pad.WEST) { onAction(1); app.toggleFavorite(g.id) }
                        BigButton("Launch with…", focus == 2) { onAction(2); onLaunchWith() }
                        BigButton("More…", focus == 3) { onAction(3); onMore() }
                    }
                    Text(launchLabel(target), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    cc.xdan.frontend.ui.components.AchievementsStrip(g, big = true)
                    g.meta.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigButton(label: String, focused: Boolean, primary: Boolean = false, suffix: String? = null, showSuffix: Boolean = false, glyph: Pad? = null, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    val corner by androidx.compose.animation.core.animateDpAsState(if (focused) 20.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    val bg = when { focused && primary -> MaterialTheme.colorScheme.primary; focused -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerHigh }
    val fg = when { focused && primary -> MaterialTheme.colorScheme.onPrimary; focused -> MaterialTheme.colorScheme.onSecondaryContainer; else -> MaterialTheme.colorScheme.onSurface }
    val interaction = remember { MutableInteractionSource() }
    Surface(onClick = onClick, shape = RoundedCornerShape(corner), color = bg, interactionSource = interaction,
        modifier = Modifier.height(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }.pressBounce(LocalPressPulse.current, focused, interaction)) {
        val style = MaterialTheme.typography.titleLarge.reactive(focused) { copy(wght = 760f, wdth = 112f, rond = 100f) }
        // The button widens with a spring while an optional suffix (e.g. the "d" of "Favourited") slides in.
        Row(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(horizontal = 32.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) { ButtonGlyph(glyph, LocalApp.current.controllers.activeFamily.collectAsState().value); Spacer(Modifier.width(12.dp)) }
            Text(label, style = style, color = fg)
            if (suffix != null) AnimatedVisibility(showSuffix,
                enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Start) +
                    slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Start) +
                    slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
                Text(suffix, style = style, color = fg)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LaunchWithOverlay(g: Game, focus: Int, onPick: (String) -> Unit) {
    val app = LocalApp.current
    val opts = remember(g) { Launchers.options(g, app.settings.value) }
    SideSheet("Launch ${g.title} with") {
        if (opts.isEmpty()) Text("Nothing can run this game yet. Install a core or add an emulator.", style = MaterialTheme.typography.bodyLarge)
        opts.forEachIndexed { i, (k, t) -> SheetItem(t.label, i == focus) { onPick(k) } }
    }
}

@Composable
private fun MenuOverlay(items: List<String>, focus: Int, title: String = "Menu", onPick: (Int) -> Unit) {
    SideSheet(title) { items.forEachIndexed { i, s -> SheetItem(s, i == focus) { onPick(i) } } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideSheet(title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))) {
        AnimatedVisibility(true, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn()) {
            Surface(Modifier.fillMaxHeight().width(440.dp).padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(bottom = 16.dp))
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetItem(label: String, focused: Boolean, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (focused) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
    val corner by androidx.compose.animation.core.animateDpAsState(if (focused) 16.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    val interaction = remember { MutableInteractionSource() }
    Text(label, Modifier.fillMaxWidth().pressBounce(LocalPressPulse.current, focused, interaction, depth = 0.95f).clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = bg))
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.titleMedium.reactive(focused, FlexRecipes.emphasize),
        color = if (focused) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
}

/** On-screen keyboard search for controllers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchOverlay(games: List<Game>, onClose: () -> Unit, onOpen: (Game) -> Unit) {
    val app = LocalApp.current
    val family by app.controllers.activeFamily.collectAsState()
    val keys = remember { ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".map { it.toString() } + listOf("SPACE", "⌫", "RESULTS")).chunked(10) }
    var query by remember { mutableStateOf("") }
    var kr by remember { mutableIntStateOf(0) }
    var kc by remember { mutableIntStateOf(0) }
    var inResults by remember { mutableStateOf(false) }
    var ri by remember { mutableIntStateOf(0) }
    val results = remember(query, games) { if (query.isBlank()) emptyList() else LibraryQuery.filter(games, Collection.All, query).take(8) }
    val resultsNow by rememberUpdatedState(results)
    var keyPulse by remember { mutableIntStateOf(0) }
    fun toResults() { if (resultsNow.isNotEmpty()) { inResults = true; ri = 0 } }
    fun press(k: String) { keyPulse++; when (k) { "SPACE" -> query = "$query "; "⌫" -> query = query.dropLast(1); "RESULTS" -> toResults(); else -> query += k.lowercase() } }
    // A physical keyboard types straight into the query.
    // While on the keyboard, the physical keyboard only types (Esc closes, Enter goes to results); in results it navigates.
    androidx.compose.runtime.DisposableEffect(inResults) {
        app.textInput = if (inResults) null else { c -> when (c) { '\u001b' -> onClose(); '\b' -> query = query.dropLast(1); '\n' -> toResults(); else -> query += c.lowercaseChar() } }
        onDispose { app.textInput = null }
    }
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            if (inResults) when (e.nav) {
                Nav.UP -> ri = (ri - 1).coerceAtLeast(0)
                Nav.DOWN -> ri = (ri + 1).coerceAtMost(resultsNow.lastIndex.coerceAtLeast(0))
                Nav.CONFIRM -> resultsNow.getOrNull(ri)?.let(onOpen)
                Nav.LEFT, Nav.BACK -> inResults = false
                Nav.SEARCH -> onClose()
                else -> {}
            } else when (e.nav) {
                Nav.UP -> kr = (kr - 1).coerceAtLeast(0)
                Nav.DOWN -> { kr = (kr + 1).coerceAtMost(keys.lastIndex); kc = kc.coerceAtMost(keys[kr].lastIndex) }
                Nav.LEFT -> kc = (kc - 1).coerceAtLeast(0)
                Nav.RIGHT -> if (kc < keys[kr].lastIndex) kc++ else toResults()
                Nav.CONFIRM -> press(keys[kr][kc.coerceAtMost(keys[kr].lastIndex)])
                Nav.ALT -> query = query.dropLast(1)
                Nav.MENU -> toResults()
                Nav.BACK, Nav.SEARCH -> onClose()
                else -> {}
            }
        }
    }
    // Centred until there's a query, then the keyboard slides left and results take the right.
    val bias by animateFloatAsState(if (query.isEmpty()) 0f else -1f, MaterialTheme.motionScheme.defaultSpatialSpec())
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)).padding(64.dp)) {
        Column(Modifier.align(androidx.compose.ui.BiasAlignment(bias, 0f)), verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = androidx.compose.ui.BiasAlignment.Horizontal(bias)) {
            Text(query.ifEmpty { "Type to search" } + "▏", style = MaterialTheme.typography.displaySmallEmphasized,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.alpha(if (inResults) 0.5f else 1f)) {
                keys.forEachIndexed { r, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowKeys.forEachIndexed { c, k ->
                            val f = !inResults && r == kr && c == kc
                            Row(Modifier.size(when { k == "⌫" -> 112.dp; k.length > 1 -> 172.dp; else -> 52.dp }, 52.dp).pressBounce(keyPulse, f, depth = 0.85f).focusEmphasis(f, 26.dp, 14.dp, 1.1f)
                                .background(if (f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { kr = r; kc = c; press(k) }, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                when (k) { "⌫" -> ButtonGlyph(Pad.WEST, family); "RESULTS" -> ButtonGlyph(Pad.START, family) }
                                Text(if (k == "RESULTS") "Results" else k, style = MaterialTheme.typography.titleMedium.reactive(f) { copy(wght = 860f, wdth = 114f, rond = 100f) })
                            }
                        }
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(query.isNotEmpty(), Modifier.align(Alignment.CenterEnd).width(460.dp),
            enter = fadeIn() + slideInHorizontally { it / 3 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (results.isEmpty()) Text("No matches", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                results.forEachIndexed { i, g ->
                    val f = inResults && i == ri
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().focusEmphasis(f, 20.dp, 20.dp, 1.03f)
                            .background(if (f) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable { onOpen(g) }.padding(8.dp)) {
                        CoverArt(g, Modifier.size(48.dp, 68.dp).clip(RoundedCornerShape(10.dp)), maxDim = 140)
                        Column {
                            Text(g.title, style = if (f) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Platforms.get(g.platformId).name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}


private fun powerAction(o: Overlay.Power, i: Int, app: cc.xdan.frontend.AppController, overlays: MutableList<Overlay>) {
    val idx = overlays.indexOf(o)
    if (o.confirming != null) { if (i == 0) { overlays.clear(); app.power(o.confirming) } else if (idx >= 0) overlays[idx] = Overlay.Power(); return }
    val p = app.powerOptions().getOrNull(i) ?: return
    if (p.confirm != null) { if (idx >= 0) overlays[idx] = Overlay.Power(confirming = p) } else { overlays.clear(); app.power(p) }
}

/** Leaves the editor and refreshes the More… menu underneath with the saved placement. */
private fun closeLogoEditor(overlays: MutableList<Overlay>, updated: Game) {
    overlays.removeLast()
    overlays.indexOfLast { it is Overlay.Manage }.takeIf { it >= 0 }?.let { i -> overlays[i] = (overlays[i] as Overlay.Manage).copy(game = updated) }
}

/** Big 16:9 preview with the nine anchors marked; the logo springs between them and grows from its anchor. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogoEditorOverlay(o: Overlay.LogoEditor) {
    val (heroPath, heroNeedsBlur) = rememberHeroArt(o.game)
    val hero by rememberImage(heroPath ?: o.game.assets.cover, 1600)
    val logo by rememberImage(o.game.assets.logo, 800)
    val motion = MaterialTheme.motionScheme
    val width by animateFloatAsState(o.width, motion.fastSpatialSpec())
    val bx by animateFloatAsState((o.pin.col - 1).toFloat(), motion.defaultSpatialSpec())
    val by by animateFloatAsState((o.pin.row - 1).toFloat(), motion.defaultSpatialSpec())
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Logo · ${o.game.title}", style = MaterialTheme.typography.headlineSmallEmphasized)
            Box(Modifier.width(960.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                hero?.let { Image(it, null, Modifier.fillMaxSize().heroBlur(heroNeedsBlur || heroPath == null), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                // The nine anchors.
                cc.xdan.frontend.model.LogoPin.entries.forEach { p ->
                    Box(Modifier.align(p.alignment()).padding(28.dp).size(if (p == o.pin) 14.dp else 10.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (p == o.pin) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.45f)))
                }
                PlacedLogo(logo, o.game.title, o.pin, width, Modifier.padding(28.dp),
                    titleStyle = MaterialTheme.typography.displaySmallEmphasized, align = androidx.compose.ui.BiasAlignment(bx, by))
            }
            Text("${o.pin.label} · ${(o.width * 100).toInt()}% wide", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val NAME_ID = "__username"
private const val HIDDEN = "__hidden"

/** Spotlight picks: what you're playing, favourites, then new arrivals — preferring games with wide art. */
internal fun featuredGames(games: List<Game>, mode: cc.xdan.frontend.model.SpotlightMode = cc.xdan.frontend.model.SpotlightMode.RECENT,
                           chosen: List<String> = emptyList(), seed: Long = 0, excluded: Set<String> = emptySet()): List<Game> {
    val visible = games.filter { !it.hidden && it.installed && it.id !in excluded }
    val recent = visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }
    val fallback = (recent + visible.filter { it.favorite } + visible.sortedByDescending { it.addedAt }).distinctBy { it.id }
    val picked = when (mode) {
        cc.xdan.frontend.model.SpotlightMode.RECENT -> recent
        cc.xdan.frontend.model.SpotlightMode.FAVOURITES -> visible.filter { it.favorite }.sortedBy { it.sortKey }
        cc.xdan.frontend.model.SpotlightMode.RANDOM -> visible.shuffled(kotlin.random.Random(seed))
        // Chosen titles show even if not installed / no emulator (the user confirmed that when adding).
        cc.xdan.frontend.model.SpotlightMode.CHOSEN -> { val byId = games.filter { !it.hidden }.associateBy { it.id }; chosen.mapNotNull { byId[it] } }
    }
    if (mode == cc.xdan.frontend.model.SpotlightMode.CHOSEN && picked.isNotEmpty()) return picked
    val ordered = picked.ifEmpty { fallback }
    val wide = ordered.filter { it.assets.hero != null || it.assets.screenshot != null }
    return (wide.ifEmpty { ordered }).take(8)
}

private fun manageItems(g: Game, s: cc.xdan.frontend.model.Settings) = listOf(if (g.hidden) "Show in library" else "Hide from library", "Rename", "Remove from library",
    // Chosen mode: you pick the list. Other modes: you can only keep a game out.
    if (s.spotlight == cc.xdan.frontend.model.SpotlightMode.CHOSEN) { if (g.id in s.spotlightGames) "Remove from spotlight" else "Add to spotlight" }
    else { if (g.id in s.spotlightHidden) "Show in spotlight" else "Hide from spotlight" },
    "Adjust logo…") +
    listOfNotNull("Reset logo to default".takeIf { g.logoPin != null || g.logoWidth != null })

private fun manageAction(o: Overlay.Manage, app: cc.xdan.frontend.AppController, overlays: MutableList<Overlay>) {
    when (o.focus) {
        0 -> { app.setHidden(setOf(o.game.id), !o.game.hidden); overlays.clear() }
        1 -> overlays += Overlay.Rename(o.game)
        2 -> if (o.armedRemove) { app.removeGames(setOf(o.game.id)); overlays.clear() }
             else overlays.indexOfFirst { it is Overlay.Manage }.takeIf { it >= 0 }?.let { overlays[it] = o.copy(armedRemove = true) }
        3 -> {
            val s = app.settings.value
            if (s.spotlight != cc.xdan.frontend.model.SpotlightMode.CHOSEN) { app.toggleSpotlightHidden(o.game.id); overlays.removeAt(overlays.lastIndex) }
            else if (o.game.id !in s.spotlightGames && app.isUnplayable(o.game))
                overlays += Overlay.ConfirmSpotlight(o.game, if (!o.game.installed) "This title isn't installed. Add anyway?" else "There's no emulator for this title. Add anyway?")
            else { app.toggleSpotlight(o.game.id); overlays.removeAt(overlays.lastIndex) }
        }
        // Each press moves the logo to the next spot; this game keeps it whatever the global setting says.
        4 -> overlays += Overlay.LogoEditor(o.game, o.game.effectiveLogoPin(app.settings.value.logoPin), o.game.effectiveLogoWidth())
        5 -> {
            app.setLogo(o.game.id, null, null)
            overlays.indexOfFirst { it is Overlay.Manage }.takeIf { it >= 0 }?.let { overlays[it] = o.copy(game = o.game.copy(logoPin = null, logoWidth = null), focus = 4) }
        }
    }
}

/**
 * Featured games in an M3 center-aligned hero carousel: one large item with smaller previews either
 * side (28dp corners, 8dp spacing per the spec). The controller moves it one item at a time.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Spotlight(games: List<Game>, index: Int, focused: Boolean, onPick: (Int) -> Unit) {
    // One spring drives both scroll and widths, so they can't disagree mid-move.
    val pos by androidx.compose.animation.core.animateFloatAsState(index.toFloat(), androidx.compose.animation.core.spring(1f, 300f))
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(top = 8.dp, bottom = 16.dp).then(Modifier.clipToBounds())) {
    val wide = maxWidth * 0.62f; val slim = maxWidth * 0.17f; val gap = 8.dp
    fun widthOf(i: Int) = slim + (wide - slim) * (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
    // Left edge of item i, minus left edge of the (fractional) current item.
    fun left(i: Int): androidx.compose.ui.unit.Dp {
        var x = 0.dp; for (j in 0 until i) x += widthOf(j) + gap
        val k = pos.toInt(); var base = 0.dp; for (j in 0 until k) base += widthOf(j) + gap
        return x - (base + (widthOf(k) + gap) * (pos - k))
    }
    for (i in games.indices) {
        val x = left(i); val w = widthOf(i)
        androidx.compose.runtime.key(games[i].id) {
        Box(Modifier.offset(x = x).width(w).fillMaxHeight()) {
        val g = games[i]
        val (heroPath, heroNeedsBlur) = rememberHeroArt(g)
        val art by rememberImage(heroPath ?: g.assets.cover, 1600)
        val logo by rememberImage(g.assets.logo, 600)
        val current = i == index
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxSize().clip(shape).then(if (current && focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onPick(i) }) {
            art?.let { Image(it, g.title, Modifier.fillMaxSize().heroBlur(heroNeedsBlur || heroPath == null), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
            val pin = rememberLogoPin(g)
            androidx.compose.animation.AnimatedVisibility(current, Modifier.fillMaxSize(), enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Box(Modifier.fillMaxSize()) {
            // Logo at its anchor; the bottom row sits above the info line.
            PlacedLogo(logo, g.title, pin, g.effectiveLogoWidth(),
                Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = if (pin.row == 2) 60.dp else 28.dp),
                titleStyle = MaterialTheme.typography.headlineMediumEmphasized)
            Column(Modifier.align(Alignment.BottomStart).padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(Platforms.get(g.platformId).name, g.lastPlayed?.let { "Played ${formatAgo(it).lowercase()}" } ?: if (g.favorite) "Favourite" else "New")
                    .joinToString("  ·  "), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            }
            }
        }
    }
    }
    }
    }
}

/** Rename with an on-screen keyboard (aA switches case). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
/** [onDone] gets the new text, or null when cancelled. */
internal fun RenameOverlay(g: Game, onDone: (String?) -> Unit) {
    val app = LocalApp.current
    var upper by remember { mutableStateOf(false) }
    val letters = "abcdefghijklmnopqrstuvwxyz0123456789".map { it.toString() }
    val keys = (letters.map { if (upper) it.uppercase() else it } + listOf(":", "-", "'", "&", "aA", "SPACE", "⌫", "SAVE")).chunked(10)
    var text by remember { mutableStateOf(g.title) }
    var kr by remember { mutableIntStateOf(0) }
    var kc by remember { mutableIntStateOf(0) }
    var keyPulse by remember { mutableIntStateOf(0) }
    val latestKeys by rememberUpdatedState(keys)
    fun press(k: String) {
        keyPulse++
        when (k) { "aA" -> upper = !upper; "SPACE" -> text += " "; "⌫" -> text = text.dropLast(1); "SAVE" -> onDone(text); else -> text += k }
    }
    // A physical keyboard types too (Esc cancels, Enter saves).
    androidx.compose.runtime.DisposableEffect(Unit) {
        app.textInput = { c -> when (c) { '\u001b' -> onDone(null); '\n' -> onDone(text); '\b' -> text = text.dropLast(1); else -> text += c } }
        onDispose { app.textInput = null }
    }
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            val ks = latestKeys
            when (e.nav) {
                Nav.UP -> kr = (kr - 1).coerceAtLeast(0)
                Nav.DOWN -> { kr = (kr + 1).coerceAtMost(ks.lastIndex); kc = kc.coerceAtMost(ks[kr].lastIndex) }
                Nav.LEFT -> kc = (kc - 1).coerceAtLeast(0)
                Nav.RIGHT -> kc = (kc + 1).coerceAtMost(ks[kr].lastIndex)
                Nav.CONFIRM -> press(ks[kr][kc.coerceAtMost(ks[kr].lastIndex)])
                Nav.ALT -> text = text.dropLast(1)
                Nav.MENU -> onDone(text)
                Nav.BACK -> onDone(null)
                else -> {}
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)).padding(64.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Rename", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text + "▏", style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEachIndexed { r, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowKeys.forEachIndexed { c, k ->
                            val f = r == kr && c == kc
                            Box(Modifier.size(if (k.length > 2) 112.dp else 52.dp, 52.dp).pressBounce(keyPulse, f, depth = 0.85f).focusEmphasis(f, 26.dp, 14.dp, 1.1f)
                                .background(if (f) MaterialTheme.colorScheme.primaryContainer else if (k == "SAVE") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { kr = r; kc = c; press(k) }, contentAlignment = Alignment.Center) {
                                Text(k, style = MaterialTheme.typography.titleMedium.reactive(f) { copy(wght = 860f, wdth = 114f, rond = 100f) })
                            }
                        }
                    }
                }
            }
        }
    }
}
