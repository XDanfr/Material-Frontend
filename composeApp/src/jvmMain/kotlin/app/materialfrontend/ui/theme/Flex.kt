package app.materialfrontend.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Google Sans Flex variable axes. Ranges (from the font's fvar table):
 * wght 1–1000, wdth 25–151, ROND 0–100, opsz 1–144, slnt −10–0.
 */
data class Axes(
    val wght: Float = 400f,
    val wdth: Float = 100f,
    val rond: Float = 0f,
    /** Optical size; null = follow the font size. */
    val opsz: Float? = null,
    val slnt: Float = 0f,
) {
    internal fun quantized(fallbackOpsz: Float) = Axes(
        wght = ((wght / 10f).roundToInt() * 10f).coerceIn(1f, 1000f),
        wdth = ((wdth / 2f).roundToInt() * 2f).coerceIn(25f, 151f),
        rond = ((rond / 5f).roundToInt() * 5f).coerceIn(0f, 100f),
        opsz = (opsz ?: fallbackOpsz).roundToInt().toFloat().coerceIn(6f, 144f),
        slnt = slnt.roundToInt().toFloat().coerceIn(-10f, 0f),
    )
}

/**
 * One variable font file, cloned per axis combination (clones share the font data, so animating
 * axes is cheap). Families are cached by their quantised axes.
 */
object Flex {
    private val base: SkTypeface by lazy {
        val bytes = Flex::class.java.getResourceAsStream("/fonts/GoogleSansFlex.ttf")!!.use { it.readBytes() }
        FontMgr.default.makeFromData(Data.makeFromBytes(bytes)) ?: error("Couldn't load Google Sans Flex")
    }
    private val families = ConcurrentHashMap<Axes, FontFamily>()
    private val axesOf = ConcurrentHashMap<FontFamily, Axes>()

    /** OpenType axis tag → Skia four-byte tag. */
    private fun v(tag: String, value: Float) =
        FontVariation(tag.fold(0) { acc, c -> (acc shl 8) or c.code }, value)

    fun family(axes: Axes, fallbackOpsz: Float = 18f): FontFamily {
        val q = axes.quantized(fallbackOpsz)
        return families.getOrPut(q) {
            val tf = base.makeClone(
                arrayOf(v("wght", q.wght), v("wdth", q.wdth), v("ROND", q.rond), v("opsz", q.opsz!!), v("slnt", q.slnt)), 0,
            )
            FontFamily(androidx.compose.ui.text.platform.Typeface(tf, "GoogleSansFlex-${q.hashCode()}")).also { axesOf[it] = q }
        }
    }

    /** Axes a style was built with (so interactions can start from the style's own settings). */
    fun axesOf(style: TextStyle): Axes =
        style.fontFamily?.let { axesOf[it] } ?: Axes(wght = (style.fontWeight?.weight ?: 400).toFloat())
}

/** Apply exact axes to a style (no fake bold/italic — the variable font does it for real). */
fun TextStyle.flex(axes: Axes): TextStyle {
    val opsz = if (fontSize.isSp) fontSize.value else 18f
    return copy(
        fontFamily = Flex.family(axes, opsz),
        fontWeight = FontWeight(axes.wght.roundToInt().coerceIn(1, 1000)),
        fontSynthesis = FontSynthesis.None,
    )
}

/** Change some axes relative to this style's current ones. */
fun TextStyle.flex(transform: Axes.() -> Axes): TextStyle = flex(Flex.axesOf(this).transform())

/**
 * Springs the font axes towards [active] ? [activeAxes] : the style's own axes. Used for hover,
 * focus, selection and press — weight, width, roundness and slant all react.
 */
@Composable
fun TextStyle.reactive(active: Boolean, activeAxes: Axes.() -> Axes): TextStyle {
    val base = Flex.axesOf(this)
    val target = if (active) base.activeAxes() else base
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val w by animateFloatAsState(target.wght, spec)
    val wd by animateFloatAsState(target.wdth, spec)
    val r by animateFloatAsState(target.rond, spec)
    val s by animateFloatAsState(target.slnt, MaterialTheme.motionScheme.fastEffectsSpec())
    return flex(Axes(w, wd, r, base.opsz, s))
}

/** Common interaction recipes. */
object FlexRecipes {
    /** Focus/selection: heavier, rounder, a touch wider. */
    val emphasize: Axes.() -> Axes = { copy(wght = (wght + 220f).coerceAtMost(900f), rond = 100f, wdth = 106f) }
    /** Hover: slightly heavier and rounder. */
    val hover: Axes.() -> Axes = { copy(wght = wght + 120f, rond = (rond + 60f).coerceAtMost(100f)) }
    /** Pressed / committed: condensed and heavy. */
    val press: Axes.() -> Axes = { copy(wght = (wght + 300f).coerceAtMost(950f), wdth = 88f) }
    /** Paused / inactive state reads as slanted. */
    val paused: Axes.() -> Axes = { copy(slnt = -10f) }
}

/**
 * M3 type scale set in Google Sans Flex. Baseline styles: roundness grows with size (displays are
 * friendlier, body text stays crisp). Emphasized styles: heavier and fully round.
 */
fun flexTypography(): Typography {
    val d = Typography()
    fun s(t: TextStyle, rond: Float, wdth: Float = 100f) = t.flex(Axes(wght = (t.fontWeight?.weight ?: 400).toFloat(), wdth = wdth, rond = rond))
    fun e(t: TextStyle, base: TextStyle) = t.flex(Axes(wght = maxOf((t.fontWeight?.weight ?: 500), (base.fontWeight?.weight ?: 400) + 200).toFloat(), wdth = 102f, rond = 100f))
    return Typography(
        displayLarge = s(d.displayLarge, 50f), displayMedium = s(d.displayMedium, 50f), displaySmall = s(d.displaySmall, 45f),
        headlineLarge = s(d.headlineLarge, 40f), headlineMedium = s(d.headlineMedium, 35f), headlineSmall = s(d.headlineSmall, 30f),
        titleLarge = s(d.titleLarge, 25f), titleMedium = s(d.titleMedium, 20f), titleSmall = s(d.titleSmall, 20f),
        bodyLarge = s(d.bodyLarge, 0f), bodyMedium = s(d.bodyMedium, 0f), bodySmall = s(d.bodySmall, 0f),
        labelLarge = s(d.labelLarge, 30f), labelMedium = s(d.labelMedium, 30f), labelSmall = s(d.labelSmall, 30f),
        displayLargeEmphasized = e(d.displayLargeEmphasized, d.displayLarge),
        displayMediumEmphasized = e(d.displayMediumEmphasized, d.displayMedium),
        displaySmallEmphasized = e(d.displaySmallEmphasized, d.displaySmall),
        headlineLargeEmphasized = e(d.headlineLargeEmphasized, d.headlineLarge),
        headlineMediumEmphasized = e(d.headlineMediumEmphasized, d.headlineMedium),
        headlineSmallEmphasized = e(d.headlineSmallEmphasized, d.headlineSmall),
        titleLargeEmphasized = e(d.titleLargeEmphasized, d.titleLarge),
        titleMediumEmphasized = e(d.titleMediumEmphasized, d.titleMedium),
        titleSmallEmphasized = e(d.titleSmallEmphasized, d.titleSmall),
        bodyLargeEmphasized = e(d.bodyLargeEmphasized, d.bodyLarge),
        bodyMediumEmphasized = e(d.bodyMediumEmphasized, d.bodyMedium),
        bodySmallEmphasized = e(d.bodySmallEmphasized, d.bodySmall),
        labelLargeEmphasized = e(d.labelLargeEmphasized, d.labelLarge),
        labelMediumEmphasized = e(d.labelMediumEmphasized, d.labelMedium),
        labelSmallEmphasized = e(d.labelSmallEmphasized, d.labelSmall),
    )
}
