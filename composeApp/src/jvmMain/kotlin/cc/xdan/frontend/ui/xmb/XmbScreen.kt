package cc.xdan.frontend.ui.xmb

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.AnimatedVisibility
import cc.xdan.frontend.model.XmbBackground
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.ui.settings.SettingItem
import cc.xdan.frontend.ui.settings.SettingsSection
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import cc.xdan.frontend.ui.settings.ChoiceStyle
import cc.xdan.frontend.ui.settings.InfoSetting
import cc.xdan.frontend.ui.settings.PanelSetting
import cc.xdan.frontend.ui.settings.ActionSetting
import cc.xdan.frontend.ui.settings.ChoiceSetting
import cc.xdan.frontend.ui.settings.ToggleSetting
import cc.xdan.frontend.ui.settings.activateSetting
import cc.xdan.frontend.ui.settings.settingsGroups
import kotlinx.coroutines.delay
import cc.xdan.frontend.ui.components.PRESS_DELAY_MS
import cc.xdan.frontend.ui.components.pressBounce
import cc.xdan.frontend.ui.components.platformIcon
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.layout.height
import cc.xdan.frontend.ui.brand.Wordmark
import cc.xdan.frontend.ui.brand.BrandArtwork
import cc.xdan.frontend.ui.theme.reactive
import cc.xdan.frontend.ui.theme.flex
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import cc.xdan.frontend.ui.components.MorphShape
import cc.xdan.frontend.ui.components.formatPlayTime
import cc.xdan.frontend.ui.components.rememberMorph
import cc.xdan.frontend.ui.theme.LocalApp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private sealed interface XmbItem { val title: String; val subtitle: String }
private data class GameItem(val game: Game) : XmbItem { override val title = game.title; override val subtitle = Platforms.get(game.platformId).shortName + (game.meta.releaseYear?.let { " · $it" } ?: "") }
private data class ActionItem(override val title: String, override val subtitle: String, val icon: ImageVector, val action: () -> Unit) : XmbItem
/** A settings page (one section of the shared settings list). */
private data class PageItem(val section: SettingsSection, override val title: String, override val subtitle: String, val icon: ImageVector) : XmbItem

private data class Category(val title: String, val icon: ImageVector, val items: List<XmbItem>)

/** PS3-style options panel on the right: a list of choices, the current one ticked. */
private data class Sidebar(val title: String, val options: List<String>, val focus: Int = 0, val current: Int? = null, val onPick: (Int) -> Unit)

private fun sectionIcon(s: SettingsSection): ImageVector = when (s) {
    SettingsSection.GENERAL -> Icons.Outlined.Palette
    SettingsSection.THEME -> Icons.Outlined.Wallpaper
    SettingsSection.LIBRARY -> Icons.Outlined.Folder
    SettingsSection.EMULATORS -> Icons.Outlined.SportsEsports
    SettingsSection.CORES -> Icons.Outlined.Extension
    SettingsSection.PLAYBACK -> Icons.Outlined.Tv
    SettingsSection.CONTROLLERS -> Icons.Outlined.Gamepad
    SettingsSection.SCRAPING -> Icons.Outlined.Image
    SettingsSection.BIOS -> Icons.Outlined.Memory
    SettingsSection.ABOUT -> Icons.Outlined.Info
}

private fun valueOf(item: SettingItem): String = when (item) {
    is ToggleSetting -> if (item.value) "On" else "Off"
    is ChoiceSetting -> item.selectedLabel
    is ActionSetting -> item.button
    is PanelSetting -> item.summary
    is InfoSetting -> item.subtitle ?: ""
}

/** Cross-media-bar: categories across, items down, settings as pages, options in a sidebar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmbScreen() {
    val app = LocalApp.current
    val games by app.library.state.collectAsState()
    val settings by app.settings.state.collectAsState()
    val family by app.controllers.activeFamily.collectAsState()

    // Settings: one page per section of the shared list (changes show in every mode).
    val groups = settingsGroups(UiMode.XMB)
    val settingsCategory = Category("Settings", Icons.Outlined.Settings, buildList {
        add(ActionItem("Library view", "Switch theme · mouse & keyboard", Icons.Outlined.GridView) { app.mode.value = UiMode.LIST; app.fullscreen.value = false })
        add(ActionItem("Big Screen", "Switch theme · controller shelves", Icons.Outlined.Tv) { app.mode.value = UiMode.BIGSCREEN })
        groups.forEach { g -> add(PageItem(g.section, g.section.labelFor(UiMode.XMB) + " Settings", g.items.take(3).joinToString(" · ") { it.title }, sectionIcon(g.section))) }
    })
    val gameCategories = remember(games) {
        val visible = games.filter { !it.hidden }
        buildList {
            visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }.take(20).takeIf { it.isNotEmpty() }?.let { add(Category("Recent", Icons.Outlined.History, it.map(::GameItem))) }
            visible.filter { it.favorite }.takeIf { it.isNotEmpty() }?.let { add(Category("Favourites", Icons.Outlined.Favorite, it.map(::GameItem))) }
            visible.filter { it.kind == GameKind.PORT }.takeIf { it.isNotEmpty() }?.let { add(Category("Ports", Icons.Outlined.Build, it.map(::GameItem))) }
            visible.groupBy { it.platformId }.toList().sortedBy { (pid, _) -> Platforms.all.indexOfFirst { it.id == pid } }.forEach { (pid, list) ->
                add(Category(Platforms.get(pid).shortName, platformIcon(pid), list.sortedBy { it.sortKey }.map(::GameItem)))
            }
        }
    }
    val categories = listOf(settingsCategory) + gameCategories
    var cat by remember { mutableIntStateOf(if (categories.size > 1) 1 else 0) }
    val itemIdx = remember { mutableStateMapOf<Int, Int>() }
    var page by remember { mutableStateOf<SettingsSection?>(null) }
    var pageIdx by remember { mutableIntStateOf(0) }
    var sidebar by remember { mutableStateOf<Sidebar?>(null) }
    if (cat > categories.lastIndex) cat = categories.lastIndex
    val current = categories.getOrNull(cat)
    val focusedItem = current?.items?.getOrNull(itemIdx[cat] ?: 0)
    val pageItems = groups.firstOrNull { it.section == page }?.items.orEmpty()
    ArtworkSeedEffect((focusedItem as? GameItem)?.game?.assets?.cover)

    fun gameOptions(g: Game) {
        val fav = if (g.favorite) "Remove from Favourites" else "Add to Favourites"
        sidebar = Sidebar(g.title, listOf(if (g.installed) "Play" else "Install", fav, "Launch with…", if (g.hidden) "Show in library" else "Hide from library", "Remove from library")) { i ->
            when (i) {
                0 -> app.play(g)
                1 -> app.toggleFavorite(g.id)
                2 -> {
                    val opts = Launchers.options(g, app.settings.value)
                    if (opts.isNotEmpty()) sidebar = Sidebar("Launch with", opts.map { it.second.label }) { k -> app.play(g, opts[k].first) }
                }
                3 -> app.setHidden(setOf(g.id), !g.hidden)
                4 -> sidebar = Sidebar("Remove ${g.title}?", listOf("Remove", "Cancel"), focus = 1) { k -> if (k == 0) app.removeGames(setOf(g.id)) }
            }
        }
    }
    fun openSetting(item: SettingItem, section: SettingsSection) {
        sidebar = when (item) {
            is ToggleSetting -> Sidebar(item.title, listOf("On", "Off"), if (item.value) 0 else 1, if (item.value) 0 else 1) { i -> item.onChange(i == 0) }
            is ChoiceSetting -> Sidebar(item.title, item.labels, item.selected, item.selected) { i -> item.onSelect(i) }
            is ActionSetting -> if (item.destructive || item.confirm != null) Sidebar(item.confirm ?: item.title, listOf(item.button, "Cancel"), focus = 1) { i -> if (i == 0) item.run() }
                else { item.run(); null }
            is PanelSetting -> { app.editInLibraryView(section); null }
            is InfoSetting -> null
        }
    }

    val cats by rememberUpdatedState(categories)
    val latestPageItems by rememberUpdatedState(pageItems)
    var pressPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            val c = cats.getOrNull(cat) ?: return@collect
            if (e.nav == Nav.CONFIRM) { pressPulse++; delay(PRESS_DELAY_MS) }
            val sb = sidebar
            val pg = page
            when {
                sb != null -> when (e.nav) {
                    Nav.UP -> sidebar = sb.copy(focus = (sb.focus - 1).coerceAtLeast(0))
                    Nav.DOWN -> sidebar = sb.copy(focus = (sb.focus + 1).coerceAtMost(sb.options.lastIndex))
                    Nav.CONFIRM -> { sidebar = null; sb.onPick(sb.focus) }
                    Nav.BACK, Nav.LEFT, Nav.SEARCH, Nav.OPTIONS -> sidebar = null
                    else -> {}
                }
                pg != null -> when (e.nav) {
                    Nav.UP -> pageIdx = (pageIdx - 1).coerceAtLeast(0)
                    Nav.DOWN -> pageIdx = (pageIdx + 1).coerceAtMost(latestPageItems.lastIndex.coerceAtLeast(0))
                    Nav.CONFIRM, Nav.RIGHT -> latestPageItems.getOrNull(pageIdx)?.let { openSetting(it, pg) }
                    Nav.BACK, Nav.LEFT -> page = null
                    else -> {}
                }
                else -> when (e.nav) {
                    Nav.LEFT -> cat = (cat - 1).coerceAtLeast(0)
                    Nav.RIGHT -> cat = (cat + 1).coerceAtMost(cats.lastIndex)
                    Nav.UP -> itemIdx[cat] = ((itemIdx[cat] ?: 0) - 1).coerceAtLeast(0)
                    Nav.DOWN -> itemIdx[cat] = ((itemIdx[cat] ?: 0) + 1).coerceAtMost(c.items.lastIndex.coerceAtLeast(0))
                    Nav.CONFIRM -> when (val it = c.items.getOrNull(itemIdx[cat] ?: 0)) {
                        is GameItem -> app.play(it.game)
                        is ActionItem -> it.action()
                        is PageItem -> { page = it.section; pageIdx = 0 }
                        null -> {}
                    }
                    Nav.SEARCH, Nav.OPTIONS -> (c.items.getOrNull(itemIdx[cat] ?: 0) as? GameItem)?.let { gameOptions(it.game) }
                    Nav.ALT -> (c.items.getOrNull(itemIdx[cat] ?: 0) as? GameItem)?.let { app.toggleFavorite(it.game.id) }
                    Nav.MENU, Nav.BACK -> app.mode.value = UiMode.BIGSCREEN
                    else -> {}
                }
            }
        }
    }

    val motion = MaterialTheme.motionScheme
    val depth by animateFloatAsState(if (page != null) 1f else 0f, motion.defaultSpatialSpec())
    Box(Modifier.fillMaxSize()) {
        XmbBackdrop(if (settings.xmbWaves) settings.xmbBackground else XmbBackground.OFF, settings.reduceMotion)
        BrandArtwork(Wordmark.HORIZONTAL, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 32.dp).height(44.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val anchorX = maxWidth * 0.22f
            val barY = maxHeight * 0.26f
            // Entering a page slides the bar and the item list left (like the PS3), leaving the page in focus.
            val shift = -(anchorX.value - 64f) * depth
            Box(Modifier.fillMaxSize().offset(x = shift.dp)) {
                // Category bar: the focused category stays anchored; others slide past it.
                categories.forEachIndexed { i, c ->
                    val dx by animateFloatAsState((i - cat) * 132f, motion.defaultSpatialSpec())
                    val sel = i == cat
                    val morph = rememberMorph(MaterialShapes.Circle, MaterialShapes.Cookie7Sided)
                    val p by animateFloatAsState(if (sel) 1f else 0f, motion.defaultSpatialSpec())
                    Column(Modifier.offset(anchorX + dx.dp - 40.dp, barY - 40.dp).width(80.dp)
                        .alpha(if (abs(i - cat) > 5) 0f else if (sel) 1f else 1f - depth),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(if (sel) 72.dp else 52.dp).clip(MorphShape(morph, p))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center) {
                            Icon(c.icon, null, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(c.title, style = MaterialTheme.typography.labelLarge.flex { copy(wdth = 86f) }.reactive(sel) { copy(wght = 760f, wdth = 112f, rond = 100f) },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).alpha(if (sel) 1f else 0.7f))
                    }
                }
                // Items of the focused category: selected sits just under the bar, earlier ones fold above it.
                current?.items?.forEachIndexed { i, item ->
                    val sel = i == (itemIdx[cat] ?: 0)
                    val rel = i - (itemIdx[cat] ?: 0)
                    val y by animateFloatAsState(if (rel < 0) rel * 84f - 150f else rel * 84f + 70f + (if (rel > 0) 72f else 0f), motion.defaultSpatialSpec())
                    if (abs(rel) <= 7) {
                        Row(Modifier.offset(anchorX - 48.dp, barY + y.dp).pressBounce(pressPulse, sel && page == null && sidebar == null, depth = 0.92f)
                            .alpha(if (sel) 1f else (0.75f - abs(rel) * 0.06f) * (1f - depth)),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            when (item) {
                                is GameItem -> CoverArt(item.game, Modifier.size(if (sel) 96.dp else 56.dp, if (sel) 136.dp else 76.dp).clip(RoundedCornerShape(if (sel) 20.dp else 12.dp)), maxDim = 300)
                                is ActionItem -> ItemIcon(item.icon, sel)
                                is PageItem -> ItemIcon(item.icon, sel)
                            }
                            Column(Modifier.alpha(1f - depth * 0.6f)) {
                                Text(item.title, style = if (sel) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMedium.flex { copy(wdth = 84f) }, maxLines = 1)
                                if (sel && depth < 0.5f) Text(item.subtitle + ((item as? GameItem)?.game?.playTimeSeconds?.takeIf { it > 0 }?.let { " · " + formatPlayTime(it) } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            // The open page: its rows to the right of the selected item, same folding as the main list.
            if (depth > 0.01f) {
                val px = anchorX + 260.dp + ((1f - depth) * 120f).dp
                pageItems.forEachIndexed { i, item ->
                    val sel = i == pageIdx
                    val rel = i - pageIdx
                    val y by animateFloatAsState(if (rel < 0) rel * 64f - 40f else rel * 64f + 70f + (if (rel > 0) 24f else 0f), motion.defaultSpatialSpec())
                    if (abs(rel) <= 9) Row(Modifier.offset(px, barY + y.dp).widthIn(max = 760.dp).alpha(depth * (if (sel) 1f else 0.8f - abs(rel) * 0.05f))
                        .pressBounce(pressPulse, sel && sidebar == null, depth = 0.94f),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(item.title, Modifier.width(380.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = if (sel) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium.flex { copy(wdth = 86f) })
                        Text(valueOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.reactive(sel) { copy(wght = 700f) },
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                pageItems.getOrNull(pageIdx)?.subtitle?.let {
                    Text(it, Modifier.offset(px, barY - 10.dp).alpha(depth), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Options sidebar (PS3 style), sliding in from the right.
        val lastSidebar = remember { mutableStateOf<Sidebar?>(null) }
        sidebar?.let { lastSidebar.value = it }
        AnimatedVisibility(sidebar != null, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
            exit = slideOutHorizontally(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec())) {
            lastSidebar.value?.let { sb -> SidebarPanel(sb, pressPulse) { i -> sidebar = null; sb.onPick(i) } }
        }
        if (settings.xmbHints) HintBar(
            when {
                sidebar != null -> listOf(Pad.SOUTH to "Select", Pad.EAST to "Back")
                page != null -> listOf(Pad.SOUTH to "Change", Pad.EAST to "Back")
                focusedItem is GameItem -> listOf(Pad.SOUTH to "Play", Pad.NORTH to "Options", Pad.WEST to "Favourite", Pad.START to "Big Screen")
                else -> listOf(Pad.SOUTH to "Open", Pad.START to "Big Screen")
            }.map { (p, l) -> (if (settings.nintendoConfirm && p == Pad.SOUTH) Pad.EAST else if (settings.nintendoConfirm && p == Pad.EAST) Pad.SOUTH else p) to l },
            family, Modifier.align(Alignment.BottomStart).padding(32.dp))
    }
}

@Composable
private fun ItemIcon(icon: ImageVector, sel: Boolean) {
    Box(Modifier.size(if (sel) 72.dp else 56.dp).clip(RoundedCornerShape(if (sel) 20.dp else 16.dp))
        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(if (sel) 32.dp else 26.dp), tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SidebarPanel(sb: Sidebar, pressPulse: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxHeight().width(400.dp), color = cs.surfaceContainerHigh.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sb.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
            sb.options.forEachIndexed { i, o ->
                val f = i == sb.focus
                Row(Modifier.fillMaxWidth().pressBounce(pressPulse, f, depth = 0.95f).clip(RoundedCornerShape(if (f) 16.dp else 28.dp))
                    .background(if (f) cs.secondaryContainer else Color.Transparent).clickable { onPick(i) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(o, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.reactive(f) { copy(wght = 720f, rond = 100f) },
                        color = if (f) cs.onSecondaryContainer else cs.onSurface)
                    if (i == sb.current) Icon(Icons.Outlined.Check, "Current", tint = if (f) cs.onSecondaryContainer else cs.primary)
                }
            }
        }
    }
}
