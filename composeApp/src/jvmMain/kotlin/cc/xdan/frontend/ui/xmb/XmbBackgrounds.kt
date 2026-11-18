@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.xmb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.graphics.shapes.Morph
import cc.xdan.frontend.model.XmbBackground
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * XMB backdrops, after the PS3/PSP: everything moves slowly on long wavelengths, colour comes from
 * the theme's roles, and the whole scene brightens and dims with the time of day (the PS3 did this
 * by the hour). Reduce motion freezes the scene on a still frame.
 */

private const val TAU = (2 * PI).toFloat()

/** Seconds since the backdrop appeared (0 while motion is reduced). */
@Composable
private fun clock(still: Boolean): Float {
    val t by produceState(0f, still) {
        if (still) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    return t
}

/** 0 at night → 1 at midday, smoothly (PS3-style time-of-day light). */
fun daylight(time: LocalTime = LocalTime.now()): Float {
    val h = time.hour + time.minute / 60f
    return ((1 - cos((h - 1f) / 24f * TAU)) / 2f).coerceIn(0f, 1f)
}

@Composable
fun XmbBackdrop(kind: XmbBackground, reduceMotion: Boolean) {
    val cs = MaterialTheme.colorScheme
    val light = remember { daylight() }
    val t = clock(reduceMotion || kind == XmbBackground.OFF)
    val shapes = remember { floatingShapes() }
    val sparkles = remember { sparkles() }
    val top = lerp(cs.surfaceContainerLowest, cs.primaryContainer, 0.06f + 0.16f * light)
    val bottom = lerp(cs.surfaceContainer, cs.tertiaryContainer, 0.10f + 0.22f * light)
    val palette = listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer, cs.tertiaryContainer)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        when (kind) {
            XmbBackground.WAVES -> { ribbons(t, palette); sparkle(t, sparkles, cs.onSurface) }
            XmbBackground.SHAPES -> { shapes(t, shapes, palette, 1f); sparkle(t, sparkles, cs.onSurface) }
            XmbBackground.SQUIGGLES -> squiggles(t, palette, 5)
            XmbBackground.MIX -> { shapes(t, shapes.take(5), palette, 0.8f); squiggles(t, palette, 3) }
            XmbBackground.OFF -> {}
        }
    }
}

// ---------------------------------------------------------------- waves (PS3 ribbon)

private fun DrawScope.ribbons(t: Float, palette: List<Color>) {
    val w = size.width; val h = size.height
    fun curve(yFrac: Float, amp: Float, wave: Float, speed: Float, phase: Float, x: Float) =
        h * yFrac + amp * sin(x / wave * TAU + t * speed + phase) + amp * 0.35f * sin(x / (wave * 2.7f) * TAU - t * speed * 0.6f)
    // A translucent band between two curves — the PS3's flowing sheet — then thin highlight lines.
    listOf(Triple(0.60f, 0.10f, 0), Triple(0.66f, -0.07f, 1)).forEach { (y, speed, ci) ->
        val band = Path()
        val steps = 96
        for (s in 0..steps) { val x = w * s / steps; val yy = curve(y, h * 0.06f, w * 0.9f, speed * TAU, ci * 1.7f, x); if (s == 0) band.moveTo(x, yy) else band.lineTo(x, yy) }
        for (s in steps downTo 0) { val x = w * s / steps; band.lineTo(x, curve(y + 0.05f, h * 0.05f, w * 1.1f, speed * TAU * 0.8f, ci * 2.3f + 1f, x)) }
        band.close()
        drawPath(band, Brush.verticalGradient(listOf(palette[ci].copy(alpha = 0.16f), palette[ci].copy(alpha = 0.03f)), startY = h * (y - 0.08f), endY = h * (y + 0.12f)))
    }
    listOf(Triple(0.62f, 0.12f, 0), Triple(0.655f, -0.09f, 1), Triple(0.70f, 0.15f, 2)).forEachIndexed { i, (y, speed, ci) ->
        val line = Path()
        for (s in 0..96) { val x = w * s / 96; val yy = curve(y, h * (0.05f - i * 0.01f), w * (0.8f + i * 0.25f), speed * TAU, i * 2f, x); if (s == 0) line.moveTo(x, yy) else line.lineTo(x, yy) }
        drawPath(line, palette[ci].copy(alpha = 0.28f - i * 0.06f), style = Stroke(2.5f - i * 0.5f))
    }
}

// ---------------------------------------------------------------- sparkles (PS3 particles)

private class Sparkle(val x: Float, val y: Float, val r: Float, val rise: Float, val twinkle: Float, val phase: Float)

private fun sparkles(): List<Sparkle> = Random(3).let { rnd ->
    List(42) { Sparkle(rnd.nextFloat(), rnd.nextFloat(), 1f + rnd.nextFloat() * 2.2f, 0.004f + rnd.nextFloat() * 0.01f, 0.2f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * TAU) }
}

private fun DrawScope.sparkle(t: Float, list: List<Sparkle>, color: Color) {
    list.forEach { p ->
        val y = ((p.y - t * p.rise) % 1f + 1f) % 1f
        val x = p.x + 0.01f * sin(t * 0.3f + p.phase)
        val a = 0.10f + 0.22f * (0.5f + 0.5f * sin(t * p.twinkle * TAU + p.phase))
        drawCircle(color.copy(alpha = a), p.r * density, Offset(x * size.width, y * size.height))
    }
}

// ---------------------------------------------------------------- floating shapes (M3 Expressive)

private class Floater(
    val morph: Morph, val x: Float, val y: Float, val sizeDp: Float, val alpha: Float, val color: Int, val outline: Boolean,
    val driftX: Float, val driftY: Float, val periodX: Float, val periodY: Float, val spin: Float, val morphPeriod: Float, val phase: Float,
)

private fun floatingShapes(): List<Floater> {
    val pairs = listOf(
        MaterialShapes.Cookie9Sided to MaterialShapes.Clover4Leaf, MaterialShapes.Sunny to MaterialShapes.Flower,
        MaterialShapes.Pill to MaterialShapes.Gem, MaterialShapes.SoftBurst to MaterialShapes.Cookie12Sided,
        MaterialShapes.Circle to MaterialShapes.Cookie6Sided, MaterialShapes.Pentagon to MaterialShapes.Puffy,
        MaterialShapes.Arch to MaterialShapes.Ghostish, MaterialShapes.Clover8Leaf to MaterialShapes.VerySunny,
        MaterialShapes.Oval to MaterialShapes.SoftBoom,
    )
    val rnd = Random(11)
    return pairs.mapIndexed { i, (a, b) ->
        Floater(
            Morph(a.normalized(), b.normalized()),
            x = (i + 0.5f) / pairs.size + (rnd.nextFloat() - 0.5f) * 0.08f, y = 0.2f + rnd.nextFloat() * 0.7f,
            sizeDp = listOf(380f, 120f, 240f, 520f, 160f, 300f, 90f, 420f, 200f)[i],
            alpha = 0.06f + rnd.nextFloat() * 0.08f, color = i % 5, outline = i % 3 == 1,
            driftX = 0.04f + rnd.nextFloat() * 0.08f, driftY = 0.03f + rnd.nextFloat() * 0.06f,
            periodX = 60f + rnd.nextFloat() * 60f, periodY = 45f + rnd.nextFloat() * 50f,
            spin = (if (i % 2 == 0) 1 else -1) * (2f + rnd.nextFloat() * 4f), morphPeriod = 18f + rnd.nextFloat() * 22f, phase = rnd.nextFloat() * TAU,
        )
    }
}

private fun DrawScope.shapes(t: Float, list: List<Floater>, palette: List<Color>, scale: Float) {
    list.forEach { f ->
        val px = f.sizeDp * density * scale
        val cx = size.width * (f.x + f.driftX * sin(t / f.periodX * TAU + f.phase))
        val cy = size.height * (f.y + f.driftY * sin(t / f.periodY * TAU + f.phase * 1.3f))
        val progress = 0.5f - 0.5f * cos(t / f.morphPeriod * TAU + f.phase)
        val path = f.morph.toPath(progress, Path()).apply { transform(Matrix().apply { scale(px, px) }) }
        translate(cx - px / 2, cy - px / 2) {
            rotate(t * f.spin + f.phase * 57f, Offset(px / 2, px / 2)) {
                if (f.outline) drawPath(path, palette[f.color].copy(alpha = f.alpha * 2.2f), style = Stroke(3f * density))
                else drawPath(path, palette[f.color].copy(alpha = f.alpha))
            }
        }
    }
}

// ---------------------------------------------------------------- squiggles (M3 wavy lines, long wavelengths)

private fun DrawScope.squiggles(t: Float, palette: List<Color>, count: Int) {
    val w = size.width; val h = size.height
    repeat(count) { i ->
        val base = h * (0.30f + 0.13f * i)
        val swell = h * (0.05f + 0.015f * i)            // the slow, screen-wide undulation
        val swellWave = w * (1.3f + 0.3f * i)
        val amp = (14f + 6f * (i % 3)) * density        // the squiggle itself
        val wave = (150f + 50f * i) * density
        val speed = (if (i % 2 == 0) 1f else -0.8f) * (0.05f + 0.015f * i)
        val p = Path()
        val steps = (w / 6f).toInt()
        for (s in 0..steps) {
            val x = w * s / steps
            val y = base + swell * sin(x / swellWave * TAU + t * speed * TAU * 0.5f + i) + amp * sin(x / wave * TAU - t * speed * TAU * 3f + i * 1.9f)
            if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, palette[i % palette.size].copy(alpha = 0.30f - i * 0.04f), style = Stroke((10f - i * 1.4f) * density, cap = StrokeCap.Round))
    }
}
