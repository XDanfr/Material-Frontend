package app.materialfrontend.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/**
 * Draws [icon]; whenever it changes, every layer morphs into its counterpart in the new icon
 * (shape, position, size and colour) while the spinning layers ([spinKeys]) turn.
 * Shape motion uses spatial springs; colour and fade use clamped progress (no overshoot).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrandMorph(
    icon: BrandIcon,
    modifier: Modifier = Modifier,
    spinKeys: Set<String> = setOf("bg", "bg2"),
    spinDegrees: Float = 360f,
    spec: AnimationSpec<Float>? = null,
    description: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val spatial = spec ?: MaterialTheme.motionScheme.slowSpatialSpec()
    var from by remember { mutableStateOf(icon) }
    var to by remember { mutableStateOf(icon) }
    var steps by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val transition = remember(from, to) { IconTransition(from, to) }
    val paths = remember { HashMap<String, Path>() }

    LaunchedEffect(icon) {
        if (icon !== to) {
            from = to; to = icon; steps++
            progress.snapTo(0f)
            progress.animateTo(1f, spatial)
        }
    }

    Canvas(modifier.semantics { description?.let { contentDescription = it } }) {
        drawTransition(transition, progress.value, cs, paths, spinKeys, spinDegrees, steps)
    }
}

/**
 * Draws [t] at [p] (may overshoot 1 with spatial springs; colour/fade are clamped).
 * HOLE layers are subtracted from the backdrop instead of painted; they and the colour blocks turn
 * with the backdrop so the cut-outs stay aligned while it spins.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun DrawScope.drawTransition(
    t: IconTransition, p: Float, cs: ColorScheme, paths: MutableMap<String, Path> = HashMap(),
    spinKeys: Set<String> = setOf("bg", "bg2"), spinDegrees: Float = 360f, steps: Int = 1,
) {
    val cp = p.coerceIn(0f, 1f)
    val k = size.minDimension / 512f
    fun center(tr: IconTransition.Track) = Offset(
        tr.fromCenter.first + (tr.toCenter.first - tr.fromCenter.first) * cp,
        tr.fromCenter.second + (tr.toCenter.second - tr.fromCenter.second) * cp,
    )
    fun angleOf(tr: IconTransition.Track) = if (tr.key in spinKeys) ((steps - 1).coerceAtLeast(0) + p) * spinDegrees else 0f
    fun isHole(r: IconRole?) = r == IconRole.HOLE
    fun holeActive(tr: IconTransition.Track) = if (cp < 0.5f) isHole(tr.fromRole) else isHole(tr.toRole)
    fun attached(tr: IconTransition.Track) = isHole(tr.fromRole) || isHole(tr.toRole) || tr.key.startsWith("block")
    fun rotated(path: Path, angle: Float, pivot: Offset): Path {
        if (angle == 0f) return path
        return Path().apply { addPath(path); transform(Matrix().apply { translate(pivot.x, pivot.y); rotateZ(angle); translate(-pivot.x, -pivot.y) }) }
    }

    for (track in t.tracks) {
        val path = paths.getOrPut(track.key) { Path() }
        path.rewind()
        track.morph.toPath(p, path)
    }
    val bg = t.tracks.firstOrNull { it.key == "bg" }
    val bgAngle = bg?.let(::angleOf) ?: 0f
    val bgPivot = bg?.let(::center) ?: Offset(256f, 256f)

    translate((size.width - 512f * k) / 2f, (size.height - 512f * k) / 2f) {
        scale(k, k, pivot = Offset.Zero) {
            for (track in t.tracks) {
                val ca = track.fromRole?.takeUnless(::isHole)?.color(cs)
                val cb = track.toRole?.takeUnless(::isHole)?.color(cs)
                if (ca == null && cb == null) continue  // pure hole: only cuts
                val color = if (ca != null && cb != null) lerp(ca, cb, cp) else (ca ?: cb)!!
                var alpha = when {
                    track.fromRole == null -> cp
                    track.toRole == null -> 1f - cp
                    else -> 1f
                }
                if (isHole(track.fromRole)) alpha = ((cp - 0.5f) * 2f).coerceIn(0f, 1f)   // hole → painted
                if (isHole(track.toRole)) alpha = (1f - cp * 2f).coerceIn(0f, 1f)        // painted → hole
                if (alpha <= 0.01f) continue
                val path = paths.getValue(track.key)
                val (angle, pivot) = if (attached(track) && track.key != "bg") bgAngle to bgPivot else angleOf(track) to center(track)
                var shape = rotated(path, angle, pivot)
                if (track.key == "bg") {
                    val holes = t.tracks.filter(::holeActive)
                    if (holes.isNotEmpty()) {
                        val cut = Path()
                        holes.forEach { h -> cut.addPath(rotated(paths.getValue(h.key), bgAngle, bgPivot)) }
                        shape = Path().apply { op(shape, cut, PathOperation.Difference) }
                    }
                }
                drawPath(shape, color, alpha)
            }
        }
    }
}

/** Static brand mark (the cartridge, or any icon from the set). */
@Composable
fun BrandMark(modifier: Modifier = Modifier, icon: BrandIcon = BrandIcons.cartridge) =
    BrandMorph(icon, modifier, description = "Material Frontend")

/**
 * Long loads: cycles through the whole icon set — backdrops spin as each icon morphs into the
 * next. [running] = false parks on the current icon.
 */
@Composable
fun BrandLoader(
    modifier: Modifier = Modifier,
    icons: List<BrandIcon> = BrandIcons.cycle,
    intervalMillis: Long = 1500,
    running: Boolean = true,
) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(running, icons) {
        while (running) { delay(intervalMillis); i = (i + 1) % icons.size }
    }
    BrandMorph(icons[i % icons.size], modifier, description = if (running) "Loading" else null)
}

/**
 * Quick loads: the cartridge stays put while its label shape runs the M3 loading-indicator
 * morph sequence, turning as it goes.
 */
@Composable
fun CartridgeLoader(modifier: Modifier = Modifier) {
    val icons = BrandIcons.cartridgeLoading
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(650); i = (i + 1) % icons.size } }
    BrandMorph(icons[i], modifier, spinKeys = setOf("core"), spinDegrees = 90f,
        spec = spring(dampingRatio = 0.6f, stiffness = 200f), description = "Loading")
}
