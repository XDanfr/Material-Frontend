package app.materialfrontend.ui.xmb

import app.materialfrontend.ui.components.platformIcon
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.layout.height
import app.materialfrontend.ui.brand.Wordmark
import app.materialfrontend.ui.brand.BrandArtwork
import app.materialfrontend.ui.theme.reactive
import app.materialfrontend.ui.theme.flex
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
import app.materialfrontend.input.Nav
import app.materialfrontend.input.Pad
import app.materialfrontend.model.Game
import app.materialfrontend.model.GameKind
import app.materialfrontend.model.Platforms
import app.materialfrontend.model.ThemeMode
import app.materialfrontend.model.UiMode
import app.materialfrontend.ui.components.ArtworkSeedEffect
import app.materialfrontend.ui.components.CoverArt
import app.materialfrontend.ui.components.HintBar
import app.materialfrontend.ui.components.MorphShape
import app.materialfrontend.ui.components.formatPlayTime
import app.materialfrontend.ui.components.rememberMorph
import app.materialfrontend.ui.theme.LocalApp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private sealed interface XmbItem { val title: String; val subtitle: String }
private data class GameItem(val game: Game) : XmbItem { override val title = game.title; override val subtitle = Platforms.get(game.platformId).shortName + (game.meta.releaseYear?.let { " · $it" } ?: "") }
private data class ActionItem(override val title: String, override val subtitle: String, val icon: ImageVector, val action: () -> Unit) : XmbItem

private data class Category(val title: String, val icon: ImageVector, val items: List<XmbItem>)

/** Cross-media-bar style launcher: categories horizontally, items vertically, soft tonal waves behind. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XmbScreen() {
    val app = LocalApp.current
    val games by app.library.state.collectAsState()
    val settings by app.settings.state.collectAsState()
    val family by app.controllers.activeFamily.collectAsState()

    val categories = remember(games, settings) {
        val visible = games.filter { !it.hidden }
        buildList {
            add(Category("Settings", Icons.Outlined.Settings, listOf(
                ActionItem("Library view", "Mouse & keyboard library", Icons.Outlined.GridView) { app.mode.value = UiMode.LIST; app.fullscreen.value = false },
                ActionItem("Big Screen", "Controller shelves", Icons.Outlined.Tv) { app.mode.value = UiMode.BIGSCREEN },
                ActionItem("Rescan library", "Look for new games", Icons.Outlined.Refresh) { app.rescan() },
                ActionItem("Theme", settings.themeMode.name.lowercase(), Icons.Outlined.Palette) { app.updateSettings { it.copy(themeMode = ThemeMode.entries[(it.themeMode.ordinal + 1) % ThemeMode.entries.size]) } },
                ActionItem("Colour from artwork", if (settings.colorFromArtwork) "On" else "Off", Icons.Outlined.ColorLens) { app.updateSettings { it.copy(colorFromArtwork = !it.colorFromArtwork) } },
            )))
            visible.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }.take(20).takeIf { it.isNotEmpty() }?.let { add(Category("Recent", Icons.Outlined.History, it.map(::GameItem))) }
            visible.filter { it.favorite }.takeIf { it.isNotEmpty() }?.let { add(Category("Favourites", Icons.Outlined.Favorite, it.map(::GameItem))) }
            visible.filter { it.kind == GameKind.PORT }.takeIf { it.isNotEmpty() }?.let { add(Category("Ports", Icons.Outlined.Build, it.map(::GameItem))) }
            visible.groupBy { it.platformId }.toList().sortedBy { (pid, _) -> Platforms.all.indexOfFirst { it.id == pid } }.forEach { (pid, list) ->
                add(Category(Platforms.get(pid).shortName, platformIcon(pid), list.sortedBy { it.sortKey }.map(::GameItem)))
            }
        }
    }
    var cat by remember { mutableIntStateOf(if (categories.size > 1) 1 else 0) }
    val itemIdx = remember { mutableStateMapOf<Int, Int>() }
    val current = categories.getOrNull(cat)
    val focusedItem = current?.items?.getOrNull(itemIdx[cat] ?: 0)
    ArtworkSeedEffect((focusedItem as? GameItem)?.game?.assets?.cover)

    val cats by rememberUpdatedState(categories)
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            val c = cats.getOrNull(cat) ?: return@collect
            when (e.nav) {
                Nav.LEFT -> cat = (cat - 1).coerceAtLeast(0)
                Nav.RIGHT -> cat = (cat + 1).coerceAtMost(cats.lastIndex)
                Nav.UP -> itemIdx[cat] = ((itemIdx[cat] ?: 0) - 1).coerceAtLeast(0)
                Nav.DOWN -> itemIdx[cat] = ((itemIdx[cat] ?: 0) + 1).coerceAtMost(c.items.lastIndex.coerceAtLeast(0))
                Nav.CONFIRM -> when (val it = c.items.getOrNull(itemIdx[cat] ?: 0)) {
                    is GameItem -> app.play(it.game)
                    is ActionItem -> it.action()
                    null -> {}
                }
                Nav.ALT -> (c.items.getOrNull(itemIdx[cat] ?: 0) as? GameItem)?.let { app.toggleFavorite(it.game.id) }
                Nav.MENU, Nav.BACK -> app.mode.value = UiMode.BIGSCREEN
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.surfaceContainer)))) {
        Waves()
        BrandArtwork(Wordmark.HORIZONTAL, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 32.dp).height(44.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val anchorX = maxWidth * 0.22f
            val barY = maxHeight * 0.26f
            // Category bar: the focused category stays anchored; others slide past it.
            categories.forEachIndexed { i, c ->
                val dx by animateFloatAsState((i - cat) * 132f, MaterialTheme.motionScheme.defaultSpatialSpec())
                val sel = i == cat
                val morph = rememberMorph(MaterialShapes.Circle, MaterialShapes.Cookie7Sided)
                val p by animateFloatAsState(if (sel) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
                Column(Modifier.offset(anchorX + dx.dp - 40.dp, barY - 40.dp).width(80.dp).alpha(if (abs(i - cat) > 5) 0f else 1f),
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
                val y by animateFloatAsState(if (rel < 0) rel * 84f - 150f else rel * 84f + 70f + (if (rel > 0) 72f else 0f), MaterialTheme.motionScheme.defaultSpatialSpec())
                if (abs(rel) <= 7) {
                    Row(Modifier.offset(anchorX - 48.dp, barY + y.dp).alpha(if (sel) 1f else 0.75f - abs(rel) * 0.06f),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (item is GameItem) CoverArt(item.game, Modifier.size(if (sel) 96.dp else 56.dp, if (sel) 136.dp else 76.dp).clip(RoundedCornerShape(if (sel) 20.dp else 12.dp)), maxDim = 300)
                        else if (item is ActionItem) Box(Modifier.size(if (sel) 72.dp else 56.dp).clip(RoundedCornerShape(if (sel) 20.dp else 16.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, Modifier.size(if (sel) 32.dp else 26.dp),
                                tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text(item.title, style = if (sel) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMedium.flex { copy(wdth = 84f) }, maxLines = 1)
                            if (sel) Text(item.subtitle + ((item as? GameItem)?.game?.playTimeSeconds?.takeIf { it > 0 }?.let { " · " + formatPlayTime(it) } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        HintBar(listOf(Pad.SOUTH to "Open", Pad.WEST to "Favourite", Pad.START to "Big Screen"), family, Modifier.align(Alignment.BottomEnd).padding(32.dp))
    }
}

/** Slow tonal waves — colour roles only, low alpha, no glow. */
@Composable
private fun Waves() {
    val t = rememberInfiniteTransition()
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart))
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val c3 = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxSize()) {
        fun wave(color: androidx.compose.ui.graphics.Color, amp: Float, freq: Float, yFrac: Float, speed: Float, width: Float) {
            val p = Path()
            val steps = 80
            for (s in 0..steps) {
                val x = size.width * s / steps
                val y = size.height * yFrac + amp * sin(freq * x / size.width * 2 * PI.toFloat() + phase * speed)
                if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(width))
        }
        wave(c1.copy(alpha = 0.22f), 60f, 1.2f, 0.62f, 1f, 3f)
        wave(c2.copy(alpha = 0.16f), 80f, 0.8f, 0.66f, -0.7f, 2f)
        wave(c3.copy(alpha = 0.12f), 40f, 1.8f, 0.7f, 1.4f, 1.5f)
    }
}
