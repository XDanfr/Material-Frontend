package cc.xdan.frontend.ui.components

import androidx.compose.ui.zIndex
import cc.xdan.frontend.data.HostOs
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import cc.xdan.frontend.ui.theme.flex
import cc.xdan.frontend.ui.theme.Axes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import cc.xdan.frontend.input.ControllerFamily
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.Platforms

/** Shape that morphs between two Material shapes (normalised polygons) by [progress]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

@Composable
fun rememberMorph(from: RoundedPolygon, to: RoundedPolygon) = remember(from, to) { Morph(from.normalized(), to.normalized()) }

/** Systems that can run right now (provided by the app root). ROMs for other systems are greyed out. */
val LocalRunnable = androidx.compose.runtime.staticCompositionLocalOf<Set<String>?> { null }

/** A ROM whose system has no installed emulator or core (kept so its history survives). */
@Composable
fun isUnavailable(game: Game): Boolean = unavailableReason(game) != null

/** "Not installed" (owned launcher game) or "No emulator" (ROM whose system can't run), else null. */
@Composable
fun unavailableReason(game: Game): String? {
    if (!game.installed) return "Not installed"
    val r = LocalRunnable.current ?: return null
    return if (game.kind == cc.xdan.frontend.model.GameKind.ROM && game.platformId !in r) "No emulator" else null
}

private val greyscale = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

/** Box art with a tonal placeholder when no artwork exists. */
@Composable
fun CoverArt(game: Game, modifier: Modifier = Modifier, maxDim: Int = 400, preferWide: Boolean = false) {
    val path = if (preferWide) game.assets.hero ?: game.assets.screenshot ?: game.assets.cover else game.assets.cover ?: game.assets.screenshot
    val img by rememberImage(path, maxDim)
    val reason = unavailableReason(game)
    val unavailable = reason != null
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .then(if (unavailable) Modifier.graphicsLayer { alpha = 0.55f } else Modifier)) {
        val bmp = img
        if (unavailable && bmp != null && maxWidth >= 90.dp) {
            androidx.compose.material3.Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(1f), shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface) {
                Text(reason ?: "", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1)
            }
        }
        if (bmp != null) {
            Image(bmp, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (unavailable) greyscale else null)
        } else if (maxWidth < 90.dp) {
            // Too small for a title: monogram.
            Text(game.title.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2).joinToString("") { it.first().uppercase() },
                Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(if (unavailable) (reason ?: "").uppercase() + " · " + Platforms.get(game.platformId).shortName.uppercase() else Platforms.get(game.platformId).shortName.uppercase(),
                    style = MaterialTheme.typography.labelMediumEmphasized, color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Focus/hover emphasis: spring scale + corner morph. No glow — tonal outline only. */
@Composable
fun Modifier.focusEmphasis(focused: Boolean, baseCorner: Dp = 12.dp, focusedCorner: Dp = 20.dp, scale: Float = 1.06f): Modifier {
    val s by animateFloatAsState(if (focused) scale else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    val corner by animateDpAsState(if (focused) focusedCorner else baseCorner, MaterialTheme.motionScheme.fastSpatialSpec())
    val outline by animateColorAsState(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.motionScheme.fastEffectsSpec())
    val pad by animateDpAsState(if (focused) 3.dp else 0.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    // Nested radii: the content sits [pad] inside the outline, so its corners are smaller by [pad]
    // (same radius would leave a sliver of gap at each corner).
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .border(3.dp, outline, RoundedCornerShape(corner))
        .padding(pad.coerceAtLeast(0.dp))
        .clip(RoundedCornerShape((corner - pad).coerceAtLeast(0.dp)))
}

/**
 * Press feedback: squish quickly while pressed, then spring back with a little overshoot
 * (M3 fast spatial spring). Triggered by a pointer press via [interaction], or — for controller /
 * keyboard confirm — by bumping [pulse] while [active] (the focused item).
 */
@Composable
fun Modifier.pressBounce(pulse: Int = 0, active: Boolean = true, interaction: InteractionSource? = null, depth: Float = 0.9f): Modifier {
    val scale = remember { Animatable(1f) }
    val release = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val pressed = interaction?.collectIsPressedAsState()?.value ?: false
    LaunchedEffect(pressed) {
        if (pressed) scale.animateTo(depth, spring(stiffness = Spring.StiffnessHigh))
        else if (scale.value != 1f) scale.animateTo(1f, release)
    }
    LaunchedEffect(pulse) {
        if (pulse > 0 && active) {
            scale.animateTo(depth, tween(70))
            scale.animateTo(1f, release)
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Keyboard shortcut labels that differ per OS. */
object Shortcuts {
    /** Fullscreen: Control-Command-F is the macOS standard (F11 is Show Desktop there); F11 elsewhere. */
    val fullscreen: String = if (HostOs.current == HostOs.MAC) "⌃⌘F" else "F11"
}

/** Bumped by a screen when the controller/keyboard confirms; the focused control bounces. */
val LocalPressPulse = compositionLocalOf { 0 }

/** How long to let a press bounce play before an action that leaves the screen. */
const val PRESS_DELAY_MS = 150L

// ---------------- Controller glyphs ----------------

/** Label/shape for a positional button on a given controller family. */
fun glyphLabel(p: Pad, f: ControllerFamily): String = when (f) {
    ControllerFamily.PLAYSTATION -> when (p) {
        Pad.SOUTH -> "✕"; Pad.EAST -> "○"; Pad.WEST -> "□"; Pad.NORTH -> "△"
        Pad.LB -> "L1"; Pad.RB -> "R1"; Pad.LT -> "L2"; Pad.RT -> "R2"; Pad.SELECT -> "Create"; Pad.START -> "Options"; Pad.GUIDE -> "PS"
        else -> p.name
    }
    ControllerFamily.NINTENDO -> when (p) {
        Pad.SOUTH -> "B"; Pad.EAST -> "A"; Pad.WEST -> "Y"; Pad.NORTH -> "X"
        Pad.LB -> "L"; Pad.RB -> "R"; Pad.LT -> "ZL"; Pad.RT -> "ZR"; Pad.SELECT -> "−"; Pad.START -> "+"; Pad.GUIDE -> "⌂"
        else -> p.name
    }
    ControllerFamily.KEYBOARD -> when (p) {
        Pad.SOUTH -> "Enter"; Pad.EAST -> "Esc"; Pad.WEST -> "F"; Pad.NORTH -> "/"
        Pad.LB -> "Q"; Pad.RB -> "E"; Pad.LT -> "["; Pad.RT -> "]"; Pad.SELECT -> "Shift"; Pad.START -> "Tab"; Pad.GUIDE -> "F1"
        Pad.UP -> "↑"; Pad.DOWN -> "↓"; Pad.LEFT -> "←"; Pad.RIGHT -> "→"
        else -> p.name
    }
    else -> when (p) { // Xbox / Steam / generic
        Pad.SOUTH -> "A"; Pad.EAST -> "B"; Pad.WEST -> "X"; Pad.NORTH -> "Y"
        Pad.LB -> "LB"; Pad.RB -> "RB"; Pad.LT -> "LT"; Pad.RT -> "RT"; Pad.SELECT -> "View"; Pad.START -> "Menu"; Pad.GUIDE -> "Guide"
        else -> p.name
    }
}

@Composable
fun ButtonGlyph(pad: Pad, family: ControllerFamily, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val face = pad in setOf(Pad.SOUTH, Pad.EAST, Pad.WEST, Pad.NORTH)
    val label = glyphLabel(pad, family)
    if (face && family == ControllerFamily.PLAYSTATION) {
        // Draw PlayStation symbols as strokes so they render identically on every OS font.
        Box(modifier.size(26.dp).background(cs.inverseSurface, CircleShape), contentAlignment = Alignment.Center) {
            val color = cs.inverseOnSurface
            Canvas(Modifier.size(12.dp)) {
                val st = Stroke(width = 2.dp.toPx())
                val w = size.width; val h = size.height
                when (pad) {
                    Pad.SOUTH -> { drawLine(color, Offset(0f, 0f), Offset(w, h), st.width); drawLine(color, Offset(w, 0f), Offset(0f, h), st.width) }
                    Pad.EAST -> drawCircle(color, w / 2, style = st)
                    Pad.WEST -> drawRect(color, style = st)
                    else -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }, color, style = st)
                }
            }
        }
    } else {
        val round = face && family != ControllerFamily.KEYBOARD
        val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
        Box(
            modifier.height(26.dp).widthIn(min = 26.dp).background(cs.inverseSurface, shape).padding(horizontal = if (round) 0.dp else 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Long labels (Options, Enter…) use a condensed width instead of shrinking.
            Text(label, color = cs.inverseOnSurface, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.flex(Axes(wght = 720f, wdth = if (label.length > 2) 78f else 100f, rond = 100f)).copy(fontSize = if (label.length > 2) 12.sp else 13.sp))
        }
    }
}

/** Bottom hint bar: glyph + action label pairs, matching the active controller. */
@Composable
fun HintBar(hints: List<Pair<Pad, String>>, family: ControllerFamily, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            // A hint with an empty label shares the next hint's label (e.g. LB RB  Tabs).
            val groups = ArrayList<Pair<List<Pad>, String>>(); var pending = ArrayList<Pad>()
            hints.forEach { (pad, label) -> pending += pad; if (label.isNotEmpty()) { groups += pending to label; pending = ArrayList() } }
            groups.forEach { (pads, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { pads.forEach { ButtonGlyph(it, family) } }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Icon for a system/collection: storefronts, computers, handhelds and home consoles read differently. */
fun platformIcon(platformId: String): ImageVector = when (platformId) {
    "steam", "epic", "ea", "xbox-pc", "gog", "ubisoft", "amazon", "battlenet", "itch" -> Icons.Outlined.Storefront
    "pc", "lutris", "dos", "msx" -> Icons.Outlined.Computer
    "gb", "gbc", "gba", "nds", "3ds", "psp", "psvita", "ngp", "wonderswan", "lynx", "gamegear", "switch", "switch2" -> Icons.Outlined.Gamepad
    "arcade", "neogeo" -> Icons.Outlined.VideogameAsset
    else -> Icons.Outlined.SportsEsports
}

fun formatPlayTime(seconds: Long): String = when {
    seconds < 60 -> "Never played".takeIf { seconds == 0L } ?: "< 1 min"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "%.1f h".format(seconds / 3600.0)
}

fun formatAgo(ms: Long?): String {
    if (ms == null) return "Never"
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "Just now"; d < 3600 -> "${d / 60} min ago"; d < 86400 -> "${d / 3600} h ago"
        d < 86400 * 30 -> "${d / 86400} days ago"; else -> java.text.SimpleDateFormat("MMM d, yyyy").format(java.util.Date(ms))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlatformChipLabel(platformId: String) =
    Text(Platforms.get(platformId).shortName, style = MaterialTheme.typography.labelMedium)
