@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.materialfrontend.ui.emulation

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.CompositionLocalProvider
import app.materialfrontend.ui.components.PRESS_DELAY_MS
import app.materialfrontend.ui.components.LocalPressPulse
import app.materialfrontend.ui.components.pressBounce
import androidx.compose.foundation.layout.Spacer
import app.materialfrontend.ui.theme.reactive
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.rememberUpdatedState
import app.materialfrontend.libretro.CoreOption
import app.materialfrontend.ui.theme.FlexRecipes
import app.materialfrontend.ui.theme.flex
import app.materialfrontend.ui.brand.BrandLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.materialfrontend.input.Nav
import app.materialfrontend.input.Pad
import app.materialfrontend.libretro.EmulationSession
import app.materialfrontend.model.VideoFilter
import app.materialfrontend.ui.bigscreen.SheetItem
import app.materialfrontend.ui.components.HintBar
import app.materialfrontend.ui.components.rememberImage
import app.materialfrontend.ui.theme.LocalApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.floor
import kotlin.math.min

/** Full-window game output + quick menu overlay (Guide / Select+Start / Esc / F1). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmulationScreen(session: EmulationSession) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val status by session.status.collectAsState()
    val error by session.error.collectAsState()
    val family by app.controllers.activeFamily.collectAsState()
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var focus by remember { mutableIntStateOf(0) }
    var slot by remember { mutableIntStateOf(1) }
    var toast by remember { mutableStateOf<String?>(null) }
    var slotsVersion by remember { mutableIntStateOf(0) }

    // Pull frames at display rate.
    LaunchedEffect(session) {
        var last = -1L
        while (true) {
            withFrameNanos { }
            val n = session.frameCounter
            if (n != last) { last = n; session.snapshot()?.let { frame = it.toComposeImageBitmap() } }
        }
    }
    LaunchedEffect(session) { session.messages.collectLatest { toast = it; delay(2200); toast = null } }

    // Hotkeys while playing: Select+RB fast-forward toggle, hold Select+LB rewind.
    LaunchedEffect(session) {
        var ffLatch = false
        while (true) {
            delay(16)
            val b = app.controllers.portButtons(0)
            fun has(p: Pad) = b and (1 shl p.ordinal) != 0
            val sel = has(Pad.SELECT)
            session.rewinding = !menuOpen && ((sel && has(Pad.LB)) || app.controllers.keyboardRewind)
            val ff = sel && has(Pad.RB)
            if (ff && !ffLatch) { session.fastForward = !session.fastForward; toast = if (session.fastForward) "Fast-forward on" else "Fast-forward off" }
            ffLatch = ff
        }
    }

    val discs by session.discs.collectAsState()
    val allOptions by session.options.collectAsState()
    val optionValues by session.optionValues.collectAsState()
    val options = allOptions.filter { it.visible && it.values.size > 1 }
    var optionsOpen by remember { mutableStateOf(false) }
    var optFocus by remember { mutableIntStateOf(0) }
    var discPick by remember { mutableIntStateOf(0) }

    val actions = buildList {
        add(MenuAction.RESUME); add(MenuAction.SAVE); add(MenuAction.LOAD)
        if (discs != null) add(MenuAction.DISC)
        add(MenuAction.RESET); add(MenuAction.FAST_FORWARD); add(MenuAction.VIDEO); add(MenuAction.INTEGER)
        if (options.isNotEmpty()) add(MenuAction.OPTIONS)
        add(MenuAction.QUIT)
    }
    fun label(a: MenuAction): String = when (a) {
        MenuAction.RESUME -> "Resume"
        MenuAction.SAVE -> "Save state · slot $slot"
        MenuAction.LOAD -> "Load state · slot $slot"
        MenuAction.DISC -> discs?.let { d -> "Disc ‹ ${d.labels.getOrNull(discPick) ?: "Disc ${discPick + 1}"} ›" + if (discPick == d.index) " · inserted" else "" } ?: ""
        MenuAction.RESET -> "Reset"
        MenuAction.FAST_FORWARD -> "Fast-forward: ${if (session.fastForward) "on" else "off"}"
        MenuAction.VIDEO -> "Video: ${settings.videoFilter.label}"
        MenuAction.INTEGER -> "Integer scaling: ${if (settings.integerScale) "on" else "off"}"
        MenuAction.OPTIONS -> "Core options (${options.size})"
        MenuAction.QUIT -> "Quit game"
    }

    fun setMenu(open: Boolean) {
        menuOpen = open; focus = 0; optionsOpen = false
        discPick = discs?.index ?: 0
        session.paused = open
        app.controllers.gameMode = !open
    }
    fun activate(a: MenuAction) {
        when (a) {
            MenuAction.RESUME -> setMenu(false)
            MenuAction.SAVE -> { session.saveState(slot); slotsVersion++; setMenu(false) }
            MenuAction.LOAD -> { session.loadState(slot); setMenu(false) }
            MenuAction.DISC -> { session.swapDisc(discPick); setMenu(false) }
            MenuAction.RESET -> { session.reset(); setMenu(false) }
            MenuAction.FAST_FORWARD -> session.fastForward = !session.fastForward
            MenuAction.VIDEO -> app.updateSettings { it.copy(videoFilter = VideoFilter.entries[(it.videoFilter.ordinal + 1) % VideoFilter.entries.size]) }
            MenuAction.INTEGER -> app.updateSettings { it.copy(integerScale = !it.integerScale) }
            MenuAction.OPTIONS -> { optionsOpen = true; optFocus = 0 }
            MenuAction.QUIT -> { setMenu(false); app.stopSession() }
        }
    }
    /** Step an option's value left/right, wrapping. */
    fun cycle(o: CoreOption, dir: Int) {
        val i = o.values.indexOfFirst { it.first == optionValues[o.key] }.coerceAtLeast(0)
        session.setOption(o.key, o.values[(i + dir + o.values.size) % o.values.size].first)
    }

    val latestActions by rememberUpdatedState(actions)
    val latestOptions by rememberUpdatedState(options)
    val latestDiscs by rememberUpdatedState(discs)
    var pressPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(session) {
        app.controllers.events.collect { e ->
            if (!menuOpen) { if (e.nav == Nav.GUIDE) setMenu(true); return@collect }
            if (e.nav == Nav.CONFIRM) { pressPulse++; if (!optionsOpen) delay(PRESS_DELAY_MS) }
            if (optionsOpen) {
                val o = latestOptions.getOrNull(optFocus)
                when (e.nav) {
                    Nav.UP -> optFocus = (optFocus - 1).coerceAtLeast(0)
                    Nav.DOWN -> optFocus = (optFocus + 1).coerceAtMost(latestOptions.lastIndex.coerceAtLeast(0))
                    Nav.LEFT -> o?.let { cycle(it, -1) }
                    Nav.RIGHT, Nav.CONFIRM -> o?.let { cycle(it, 1) }
                    Nav.ALT -> session.resetOptions()
                    Nav.BACK -> optionsOpen = false
                    Nav.GUIDE, Nav.MENU -> setMenu(false)
                    else -> {}
                }
                return@collect
            }
            val a = latestActions.getOrNull(focus)
            when (e.nav) {
                Nav.UP -> focus = (focus - 1).coerceAtLeast(0)
                Nav.DOWN -> focus = (focus + 1).coerceAtMost(latestActions.lastIndex)
                Nav.LEFT -> when (a) {
                    MenuAction.SAVE, MenuAction.LOAD -> slot = (slot - 1).coerceAtLeast(1)
                    MenuAction.DISC -> latestDiscs?.let { discPick = (discPick - 1 + it.count) % it.count }
                    else -> {}
                }
                Nav.RIGHT -> when (a) {
                    MenuAction.SAVE, MenuAction.LOAD -> slot = (slot + 1).coerceAtMost(4)
                    MenuAction.DISC -> latestDiscs?.let { discPick = (discPick + 1) % it.count }
                    else -> {}
                }
                Nav.CONFIRM -> a?.let(::activate)
                Nav.BACK, Nav.GUIDE, Nav.MENU -> setMenu(false)
                else -> {}
            }
        }
    }

    CompositionLocalProvider(LocalPressPulse provides pressPulse) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val img = frame
        if (img != null) GameCanvas(img, session.aspectRatio, settings.videoFilter, settings.integerScale)
        if (status == EmulationSession.Status.LOADING) BrandLoader(Modifier.align(Alignment.Center).size(160.dp), intervalMillis = 1100)
        if (status == EmulationSession.Status.ERROR) {
            Surface(Modifier.align(Alignment.Center).width(520.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Couldn't start the game", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.onErrorContainer)
                    Button(shapes = ButtonDefaults.shapes(), onClick = { app.stopSession() }) { Text("Back to library") }
                }
            }
        }

        AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(24.dp),
            enter = slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } + fadeIn(), exit = fadeOut()) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(toast.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }

        AnimatedVisibility(menuOpen, enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()), exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))) {
                AnimatedVisibility(menuOpen, Modifier.align(Alignment.CenterStart), enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 8 } + fadeIn(),
                    exit = slideOutVertically { it / 8 } + fadeOut()) {
                    Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Surface(Modifier.width(420.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Paused", style = MaterialTheme.typography.headlineSmallEmphasized.flex(FlexRecipes.paused), modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                                actions.forEachIndexed { i, a -> SheetItem(label(a), i == focus && !optionsOpen) { focus = i; activate(a) } }
                            }
                        }
                        val current = actions.getOrNull(focus)
                        when {
                            optionsOpen -> CoreOptionsPanel(options, optionValues, optFocus, onPick = { i, o -> optFocus = i; cycle(o, 1) })
                            current == MenuAction.SAVE || current == MenuAction.LOAD -> SlotPreview(session, slot, slotsVersion)
                        }
                    }
                }
                HintBar(
                    if (optionsOpen) listOf(Pad.SOUTH to "Change", Pad.LEFT to "Previous value", Pad.WEST to "Reset all", Pad.EAST to "Back")
                    else listOf(Pad.SOUTH to "Select", Pad.LEFT to "Slot / disc", Pad.EAST to "Resume"),
                    family, Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun SlotPreview(session: EmulationSession, slot: Int, version: Int) {
    val s = remember(slot, version) { session.slots()[slot] }
    val thumb by rememberImage(s.thumbnail.takeIf { it.isFile }?.path, 640)
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp).width(360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Slot $slot", style = MaterialTheme.typography.titleLarge)
            val t = thumb
            if (t != null) Image(t, null, Modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
            else Box(Modifier.aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) { Text("Empty") }
            if (s.file.isFile) Text(app.materialfrontend.ui.components.formatAgo(s.modified), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GameCanvas(img: ImageBitmap, aspect: Float, filter: VideoFilter, integer: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val ar = if (aspect > 0) aspect else img.width / img.height.toFloat()
        var h = size.height; var w = h * ar
        if (w > size.width) { w = size.width; h = w / ar }
        if (integer) {
            val k = floor(min(size.width / img.width, size.height / img.height)).coerceAtLeast(1f)
            h = img.height * k; w = h * ar
        }
        val x = (size.width - w) / 2; val y = (size.height - h) / 2
        drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
            filterQuality = if (filter == VideoFilter.SMOOTH) FilterQuality.Low else FilterQuality.None)
        val lineH = h / img.height
        if (filter == VideoFilter.SCANLINES && lineH >= 2f) {
            var yy = y + lineH * 0.5f
            while (yy < y + h) { drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, yy), Offset(x + w, yy), strokeWidth = lineH * 0.45f); yy += lineH }
        }
        if (filter == VideoFilter.LCD && lineH >= 3f) {
            val colW = w / img.width
            var yy = y; while (yy < y + h) { drawLine(Color.Black.copy(alpha = 0.25f), Offset(x, yy), Offset(x + w, yy), strokeWidth = 1f); yy += lineH }
            var xx = x; while (xx < x + w) { drawLine(Color.Black.copy(alpha = 0.25f), Offset(xx, y), Offset(xx, y + h), strokeWidth = 1f); xx += colW }
        }
    }
}

private enum class MenuAction { RESUME, SAVE, LOAD, DISC, RESET, FAST_FORWARD, VIDEO, INTEGER, OPTIONS, QUIT }

/** Core options, grouped by category (v2 cores); left/right or click cycles the value. Saved per core. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoreOptionsPanel(options: List<CoreOption>, values: Map<String, String>, focus: Int, onPick: (Int, CoreOption) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(focus) { if (options.isNotEmpty()) listState.animateScrollToItem((focus - 2).coerceAtLeast(0)) }
    Surface(Modifier.width(560.dp).heightIn(max = 620.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp)) {
            Text("Core options", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            Text("Saved for this core. Some options apply after a reset.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(options, key = { _, o -> o.key }) { i, o ->
                    val f = i == focus
                    val showCategory = o.category != null && (i == 0 || options[i - 1].category != o.category)
                    Column {
                        if (showCategory) Text(o.category!!, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
                        val bg by animateFloatAsState(if (f) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec())
                        Row(Modifier.fillMaxWidth().pressBounce(LocalPressPulse.current, f, depth = 0.96f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = bg))
                            .clickable { onPick(i, o) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.reactive(f) { copy(wght = 620f, rond = 80f) }, maxLines = 2)
                            val v = values[o.key]
                            Text("‹ " + (o.values.firstOrNull { it.first == v }?.second ?: v ?: "") + " ›",
                                style = MaterialTheme.typography.labelLarge.reactive(f) { copy(wght = 720f, wdth = 108f) },
                                color = if (v != o.default) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
