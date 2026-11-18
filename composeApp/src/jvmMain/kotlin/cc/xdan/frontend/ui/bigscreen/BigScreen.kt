package cc.xdan.frontend.ui.bigscreen

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
}

fun buildShelves(games: List<Game>): List<Shelf> {
    val visible = games.filter { !it.hidden }
    val out = ArrayList<Shelf>()
    visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }.take(15).takeIf { it.isNotEmpty() }?.let { out += Shelf("Continue playing", it) }
    visible.filter { it.favorite }.sortedBy { it.sortKey }.takeIf { it.isNotEmpty() }?.let { out += Shelf("Favourites", it) }
    visible.sortedByDescending { it.addedAt }.take(15).takeIf { it.isNotEmpty() }?.let { out += Shelf("Recently added", it) }
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
    val featured = remember(games) { featuredGames(games) }
    var row by remember { mutableIntStateOf(-1) }
    var spotIdx by remember { mutableIntStateOf(0) }
    var barFocus by remember { mutableStateOf(false) }
    if (row == -1 && featured.isEmpty()) row = 0
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
        Tab.HOME -> if (row == -1) featured.getOrNull(spotIdx) else shelves.getOrNull(row)?.games?.getOrNull(cols[row] ?: 0)
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

    val currentFocused by rememberUpdatedState(focused)
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
                    val n = manageItems(top.game).size
                    when (e.nav) {
                        Nav.UP -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus - 1).coerceAtLeast(0), armedRemove = false)
                        Nav.DOWN -> overlays[overlays.lastIndex] = top.copy(focus = (top.focus + 1).coerceAtMost(n - 1), armedRemove = false)
                        Nav.CONFIRM -> manageAction(top, app, overlays)
                        Nav.BACK, Nav.MENU -> overlays.removeLast()
                        else -> {}
                    }
                }
                is Overlay.Rename, Overlay.Search -> { /* handled by their own overlay */ }
                null -> if (barFocus) when (e.nav) {
                    Nav.LEFT, Nav.PREV_TAB -> tab = Tab.entries[(tab.ordinal + Tab.entries.size - 1) % Tab.entries.size]
                    Nav.RIGHT, Nav.NEXT_TAB -> tab = Tab.entries[(tab.ordinal + 1) % Tab.entries.size]
                    Nav.DOWN, Nav.CONFIRM, Nav.BACK -> barFocus = false
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
                        val top0 = if (featured.isEmpty()) 0 else -1
                        if (row == -1) when (e.nav) {
                            Nav.UP -> barFocus = true
                            Nav.DOWN -> if (shelves.isNotEmpty()) row = 0
                            Nav.LEFT -> spotIdx = (spotIdx - 1).coerceAtLeast(0)
                            Nav.RIGHT -> spotIdx = (spotIdx + 1).coerceAtMost(featured.lastIndex)
                            else -> {}
                        } else when (e.nav) {
                            Nav.UP -> if (row == top0) barFocus = true else row = (row - 1).coerceAtLeast(top0)
                            Nav.DOWN -> row = (row + 1).coerceAtMost((shelves.size - 1).coerceAtLeast(0))
                            Nav.LEFT -> cols[row] = ((cols[row] ?: 0) - 1).coerceAtLeast(0)
                            Nav.RIGHT -> cols[row] = ((cols[row] ?: 0) + 1).coerceAtMost((shelves.getOrNull(row)?.games?.size ?: 1) - 1)
                            Nav.PREV_PAGE -> cols[row] = 0
                            Nav.NEXT_PAGE -> cols[row] = (shelves.getOrNull(row)?.games?.size ?: 1) - 1
                            else -> {}
                        }
                    } else {
                        when (e.nav) {
                            Nav.LEFT -> gridIdx = (gridIdx - 1).coerceAtLeast(0)
                            Nav.RIGHT -> gridIdx = (gridIdx + 1).coerceAtMost(libGames.lastIndex.coerceAtLeast(0))
                            Nav.UP -> if (gridIdx < gridCols) barFocus = true else gridIdx = (gridIdx - gridCols).coerceAtLeast(0)
                            Nav.DOWN -> gridIdx = (gridIdx + gridCols).coerceAtMost(libGames.lastIndex.coerceAtLeast(0))
                            Nav.PREV_PAGE -> { platformIdx = (platformIdx - 1 + platforms.size) % platforms.size; gridIdx = 0 }
                            Nav.NEXT_PAGE -> { platformIdx = (platformIdx + 1) % platforms.size; gridIdx = 0 }
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
            TopBar(tab, family, barFocus) { tab = it; barFocus = false }
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
                    Tab.LIBRARY -> LibraryTab(platforms, platformIdx, libGames, gridIdx, onCols = { gridCols = it }, onClick = { i -> if (gridIdx == i) overlays += Overlay.Details(libGames[i]) else gridIdx = i })
                }
            }
        }
        if (settings.bigScreenHints) HintBar(
            when (overlays.lastOrNull()) {
                is Overlay.Details -> listOf(Pad.SOUTH to "Select", Pad.WEST to "Favourite", Pad.EAST to "Back")
                is Overlay.Manage -> listOf(Pad.SOUTH to "Select", Pad.EAST to "Back")
                is Overlay.Rename -> listOf(Pad.SOUTH to "Type", Pad.WEST to "Delete", Pad.START to "Save", Pad.EAST to "Cancel")
                is Overlay.Menu, is Overlay.LaunchWith -> listOf(Pad.SOUTH to "Select", Pad.EAST to "Close")
                Overlay.Search -> listOf(Pad.SOUTH to "Type", Pad.WEST to "Delete", Pad.START to "Open result", Pad.EAST to "Close")
                null -> if (barFocus) listOf(Pad.LEFT to "Switch tab", Pad.DOWN to "Back to content")
                        else if (tab == Tab.SETTINGS) listOf(Pad.SOUTH to "Change", Pad.LEFT to "Value", Pad.LB to "Tabs", Pad.EAST to "Home")
                        else listOf(Pad.SOUTH to "Details", Pad.WEST to "Favourite", Pad.NORTH to "Search", Pad.LB to "Tabs", Pad.START to "Menu")
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
                is Overlay.Manage -> MenuOverlay(manageItems(o.game).map { if (it == "Remove from library" && o.armedRemove) "Press again to remove" else it }, o.focus) { i ->
                    overlays[overlays.indexOf(o)] = o.copy(focus = i); manageAction(o.copy(focus = i), app, overlays)
                }
                is Overlay.Rename -> RenameOverlay(o.game, onDone = { t -> if (t.isNotBlank()) app.rename(o.game.id, t.trim()); overlays.remove(o) })
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
private fun TopBar(tab: Tab, family: ControllerFamily, barFocus: Boolean, onTab: (Tab) -> Unit) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            AnimatedContent(if (row == -1) null else focused, transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 8 })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            }, contentKey = { it?.id ?: "spotlight" }) { g ->
                if (g != null) HeroInfo(g)
                else if (featured.isNotEmpty()) Spotlight(featured, spotIdx, row == -1 && contentFocus, onSpot)
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
                            Box(Modifier.width(if (active) 168.dp else 132.dp).aspectRatio(2f / 3f).pressBounce(LocalPressPulse.current, f)
                                .focusEmphasis(f, baseCorner = 16.dp, focusedCorner = 28.dp, scale = 1.1f)
                                .clickable { onClick(r, c) }) {
                                CoverArt(g, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
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
private fun LibraryTab(platforms: List<String?>, platformIdx: Int, games: List<Game>, index: Int, onCols: (Int) -> Unit, onClick: (Int) -> Unit) {
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
                    Box(Modifier.aspectRatio(2f / 3f).pressBounce(LocalPressPulse.current, i == index).focusEmphasis(i == index, 16.dp, 28.dp, 1.08f).clickable { onClick(i) }) { CoverArt(g, Modifier.fillMaxSize()) }
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
    val hero by rememberImage(g.assets.hero ?: g.assets.screenshot, 1920)
    val logo by rememberImage(g.assets.logo, 800)
    val target = remember(g, settings) { Launchers.resolve(g, settings) }
    AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.96f)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            hero?.let { Image(it, null, Modifier.fillMaxWidth().fillMaxHeight(0.62f), contentScale = ContentScale.Crop) }
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
                        BigButton("Play", focus == 0, primary = true) { onAction(0); onPlay() }
                        BigButton("Favourite", focus == 1, suffix = "d", showSuffix = g.favorite) { onAction(1); app.toggleFavorite(g.id) }
                        BigButton("Launch with…", focus == 2) { onAction(2); onLaunchWith() }
                        BigButton("More…", focus == 3) { onAction(3); onMore() }
                    }
                    Text(launchLabel(target), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    g.meta.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigButton(label: String, focused: Boolean, primary: Boolean = false, suffix: String? = null, showSuffix: Boolean = false, onClick: () -> Unit) {
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
private fun MenuOverlay(items: List<String>, focus: Int, onPick: (Int) -> Unit) {
    SideSheet("Menu") { items.forEachIndexed { i, s -> SheetItem(s, i == focus) { onPick(i) } } }
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
    val keys = remember { ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".map { it.toString() } + listOf("SPACE", "⌫")).chunked(10) }
    var query by remember { mutableStateOf("") }
    var kr by remember { mutableIntStateOf(0) }
    var kc by remember { mutableIntStateOf(0) }
    val results = remember(query, games) { if (query.isBlank()) emptyList() else LibraryQuery.filter(games, Collection.All, query).take(8) }
    var keyPulse by remember { mutableIntStateOf(0) }
    fun press(k: String) { keyPulse++; query = when (k) { "SPACE" -> "$query "; "⌫" -> query.dropLast(1); else -> query + k.lowercase() } }
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            when (e.nav) {
                Nav.UP -> kr = (kr - 1).coerceAtLeast(0)
                Nav.DOWN -> kr = (kr + 1).coerceAtMost(keys.lastIndex)
                Nav.LEFT -> kc = (kc - 1).coerceAtLeast(0)
                Nav.RIGHT -> kc = (kc + 1).coerceAtMost(keys[kr].lastIndex)
                Nav.CONFIRM -> press(keys[kr][kc.coerceAtMost(keys[kr].lastIndex)])
                Nav.ALT -> query = query.dropLast(1)
                Nav.MENU -> results.firstOrNull()?.let(onOpen)
                Nav.BACK, Nav.SEARCH -> onClose()
                else -> {}
            }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)).padding(64.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(query.ifEmpty { "Search" } + "▏", style = MaterialTheme.typography.displaySmallEmphasized,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEachIndexed { r, rowKeys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowKeys.forEachIndexed { c, k ->
                                val f = r == kr && c == kc
                                Box(Modifier.size(if (k.length > 1) 112.dp else 52.dp, 52.dp).pressBounce(keyPulse, f, depth = 0.85f).focusEmphasis(f, 26.dp, 14.dp, 1.1f)
                                    .background(if (f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { kr = r; kc = c; press(k) }, contentAlignment = Alignment.Center) {
                                    Text(k, style = MaterialTheme.typography.titleMedium.reactive(f) { copy(wght = 860f, wdth = 114f, rond = 100f) })
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    results.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.clickable { onOpen(g) }) {
                            CoverArt(g, Modifier.size(48.dp, 68.dp).clip(RoundedCornerShape(10.dp)), maxDim = 140)
                            Column {
                                Text(g.title, style = if (i == 0) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium)
                                Text(Platforms.get(g.platformId).name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}


private const val HIDDEN = "__hidden"

/** Spotlight picks: what you're playing, favourites, then new arrivals — preferring games with wide art. */
private fun featuredGames(games: List<Game>): List<Game> {
    val visible = games.filter { !it.hidden }
    val ordered = (visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed } +
        visible.filter { it.favorite } + visible.sortedByDescending { it.addedAt }).distinctBy { it.id }
    val wide = ordered.filter { it.assets.hero != null || it.assets.screenshot != null }
    return (wide.ifEmpty { ordered }).take(8)
}

private fun manageItems(g: Game) = listOf(if (g.hidden) "Show in library" else "Hide from library", "Rename", "Remove from library")

private fun manageAction(o: Overlay.Manage, app: cc.xdan.frontend.AppController, overlays: MutableList<Overlay>) {
    when (o.focus) {
        0 -> { app.setHidden(setOf(o.game.id), !o.game.hidden); overlays.clear() }
        1 -> overlays += Overlay.Rename(o.game)
        2 -> if (o.armedRemove) { app.removeGames(setOf(o.game.id)); overlays.clear() }
             else overlays.indexOfFirst { it is Overlay.Manage }.takeIf { it >= 0 }?.let { overlays[it] = o.copy(armedRemove = true) }
    }
}

/**
 * Featured games in an M3 center-aligned hero carousel: one large item with smaller previews either
 * side (28dp corners, 8dp spacing per the spec). The controller moves it one item at a time.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Spotlight(games: List<Game>, index: Int, focused: Boolean, onPick: (Int) -> Unit) {
    val state = rememberCarouselState(initialItem = index) { games.size }
    LaunchedEffect(index) { state.animateScrollToItem(index) }
    HorizontalCenteredHeroCarousel(state, Modifier.fillMaxSize().padding(top = 8.dp, bottom = 16.dp), itemSpacing = 8.dp) { i ->
        val g = games[i]
        val art by rememberImage(g.assets.hero ?: g.assets.screenshot ?: g.assets.cover, 1600)
        val logo by rememberImage(g.assets.logo, 600)
        val current = i == index
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxSize().maskClip(shape).then(if (current && focused) Modifier.maskBorder(androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onPick(i) }) {
            art?.let { Image(it, g.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
            Column(Modifier.align(Alignment.BottomStart).padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val l = logo
                if (l != null) Image(l, g.title, Modifier.heightIn(max = 96.dp).widthIn(max = 380.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
                else Text(g.title, style = MaterialTheme.typography.headlineMediumEmphasized, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(Platforms.get(g.platformId).name, g.lastPlayed?.let { "Played ${formatAgo(it).lowercase()}" } ?: if (g.favorite) "Favourite" else "New")
                    .joinToString("  ·  "), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

/** Rename with an on-screen keyboard (aA switches case). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RenameOverlay(g: Game, onDone: (String) -> Unit) {
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
                Nav.BACK -> onDone("")
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
