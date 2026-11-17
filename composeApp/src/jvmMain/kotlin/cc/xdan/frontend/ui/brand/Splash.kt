package cc.xdan.frontend.ui.brand

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cc.xdan.frontend.ui.theme.Axes
import cc.xdan.frontend.ui.theme.Flex
import cc.xdan.frontend.ui.theme.LocalApp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Wordmark type settings (Google Sans Flex). */
object WordmarkType {
    /** "Material": 820 wght, 118 wdth, 100 ROND, 144 opsz, 0 slnt, −1% tracking. */
    val material = Axes(wght = 820f, wdth = 118f, rond = 100f, opsz = 144f, slnt = 0f)
    const val MATERIAL_TRACKING = -0.01f
    /** "Frontend": 330 wght, 96 wdth, 0 ROND, 144 opsz, 0 slnt, +0.5% tracking. */
    val frontend = Axes(wght = 330f, wdth = 96f, rond = 0f, opsz = 144f, slnt = 0f)
    const val FRONTEND_TRACKING = 0.005f
}

/** Timeline (ms). */
private object T {
    const val MORPH_1 = 420L      // circle → 9-sided cookie
    const val MORPH_2 = 860L      // cookie → flower (the logo)
    const val TEXT_IN = 1250L     // wordmark slides out beside the cartridge
    const val WAVE = 1380L        // "Material" swells letter by letter
    const val LETTER_STAGGER = 55L
    const val LETTER_MS = 440L
    const val END = 2750L
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Ease-out with a little overshoot, so each letter "lands" with a bounce (spatial feel). */
private fun backOut(x: Float): Float {
    val c1 = 1.70158f; val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

private fun span(axes: Axes, tracking: Float, size: TextUnit) = SpanStyle(
    fontFamily = Flex.family(axes, size.value),
    fontWeight = FontWeight(axes.wght.roundToInt().coerceIn(1, 1000)),
    fontSynthesis = FontSynthesis.None,
    letterSpacing = tracking.em,
)

/** "Material" goes from Frontend's thin settings to its own, one letter after another (a weight wave). */
fun animatedWordmark(elapsed: Long, size: TextUnit): AnnotatedString = buildAnnotatedString {
    val from = WordmarkType.frontend; val to = WordmarkType.material
    "Material".forEachIndexed { i, ch ->
        val start = T.WAVE + i * T.LETTER_STAGGER
        val p = ((elapsed - start).toFloat() / T.LETTER_MS).coerceIn(0f, 1f)
        val e = backOut(p)
        val axes = Axes(
            wght = lerp(from.wght, to.wght, e).coerceIn(1f, 1000f),
            wdth = lerp(from.wdth, to.wdth, e).coerceIn(25f, 151f),
            rond = lerp(from.rond, to.rond, e).coerceIn(0f, 100f),
            opsz = 144f,
        )
        withStyle(span(axes, lerp(WordmarkType.FRONTEND_TRACKING, WordmarkType.MATERIAL_TRACKING, p), size)) { append(ch) }
    }
    withStyle(span(WordmarkType.frontend, WordmarkType.FRONTEND_TRACKING, size)) { append(" Frontend") }
}

/** Two colours as in the wordmark: "Material" in primary, "Frontend" in on-surface. */
private fun coloured(text: AnnotatedString, material: androidx.compose.ui.graphics.Color, frontend: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = material), 0, "Material".length)
    addStyle(SpanStyle(color = frontend), "Material".length, text.length)
}

/** True while the launch animation covers the screen (provided by App; setup's welcome step waits for it). */
val LocalSplashActive = compositionLocalOf { false }

/** Lockup sizes shared by the launch animation and setup's welcome step (so the hand-off lines up). */
val LOCKUP_ICON = 150.dp
val LOCKUP_TEXT = 76.sp

/** The finished logo: cartridge + live Google Sans Flex wordmark, laid out exactly like the end of the launch animation. */
@Composable
fun WordmarkLockup(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMorph(BrandIcons.cartridge, Modifier.size(LOCKUP_ICON), description = "Material Frontend")
        Text(coloured(animatedWordmark(Long.MAX_VALUE / 4, LOCKUP_TEXT), cs.primary, cs.onSurface), style = TextStyle(fontSize = LOCKUP_TEXT), maxLines = 1, softWrap = false)
    }
}

/**
 * Launch animation: a shape morphs twice inside the cartridge, settles into the flower, then the
 * wordmark slides out and "Material" swells into its heavy, round form. Click / any key / any
 * controller button skips. Driven by the frame clock (deterministic for tests).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaunchSplash(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val done by rememberUpdatedState(onDone)
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < T.END) elapsed = withFrameMillis { it } - start
        done()
    }
    LaunchedEffect(Unit) { app.controllers.events.collect { done() } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val icon = when {
        elapsed < T.MORPH_1 -> BrandIcons.splash[0]
        elapsed < T.MORPH_2 -> BrandIcons.splash[1]
        else -> BrandIcons.splash[2]
    }
    val intro by animateFloatAsState(if (elapsed > 0) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 260f))
    val size = LOCKUP_TEXT
    val cs = MaterialTheme.colorScheme

    Box(
        modifier.fillMaxSize().background(cs.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() }
            .focusRequester(focus).focusable().onKeyEvent { done(); true },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            BrandMorph(icon, Modifier.size(LOCKUP_ICON).graphicsLayer { scaleX = 0.8f + 0.2f * intro; scaleY = scaleX; alpha = intro.coerceIn(0f, 1f) },
                spinKeys = setOf("core"), spinDegrees = 90f, spec = spring(dampingRatio = 0.62f, stiffness = 220f), description = "Material Frontend")
            AnimatedVisibility(elapsed >= T.TEXT_IN,
                enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 300f), expandFrom = Alignment.Start) + fadeIn(spring(stiffness = 300f))) {
                // Two colours as in the wordmark: "Material" in primary, "Frontend" in on-surface.
                Text(coloured(animatedWordmark(elapsed, size), cs.primary, cs.onSurface), style = TextStyle(fontSize = size), maxLines = 1, softWrap = false)
            }
        }
    }
}
