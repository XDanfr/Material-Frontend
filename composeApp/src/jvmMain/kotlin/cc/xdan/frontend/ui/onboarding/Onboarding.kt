package cc.xdan.frontend.ui.onboarding

import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.animation.core.Animatable
import cc.xdan.frontend.ui.brand.LocalSplashActive
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.LauncherSources
import cc.xdan.frontend.data.SteamLibrary
import cc.xdan.frontend.emulators.BiosCatalog
import cc.xdan.frontend.emulators.CoreCatalog
import cc.xdan.frontend.input.ControllerFamily
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.RomFolder
import cc.xdan.frontend.model.Settings
import cc.xdan.frontend.model.ThemeMode
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.Usage
import cc.xdan.frontend.ui.brand.BrandIcons
import cc.xdan.frontend.ui.brand.BrandMorph
import cc.xdan.frontend.ui.brand.CartridgeLoader
import cc.xdan.frontend.ui.brand.WordmarkLockup
import cc.xdan.frontend.ui.brand.BrandTile
import cc.xdan.frontend.ui.brand.BrandTileImage
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import cc.xdan.frontend.ui.components.ButtonGlyph
import cc.xdan.frontend.ui.components.FilePicker
import cc.xdan.frontend.ui.components.HintBar
import cc.xdan.frontend.ui.components.LocalPressPulse
import cc.xdan.frontend.ui.components.PRESS_DELAY_MS
import cc.xdan.frontend.ui.components.pressBounce
import cc.xdan.frontend.ui.settings.recommendedCores
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.reactive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Setup, one question per screen. Everything is reachable with a controller (D-pad/stick to move,
 * A to choose, B to go back, Start to continue), the keyboard (arrows, Enter, Esc) or the mouse.
 * Fullscreen (controller start-up, F11 / ⌃⌘F on macOS) uses larger type — same flow, same saved progress.
 */

private enum class StepId { WELCOME, USAGE, LOOK, CONTROLS, NINTENDO, PC_LIBRARIES, FOLDERS, EMULATORS, CORES, BIOS, ARTWORK, DONE }

/** Which screens apply, given the answers so far. */
private fun steps(s: Settings, nintendoPad: Boolean): List<StepId> = buildList {
    add(StepId.WELCOME); add(StepId.USAGE); add(StepId.LOOK); add(StepId.CONTROLS)
    if (nintendoPad) add(StepId.NINTENDO)
    val usage = s.usage ?: Usage.BOTH
    // Launchers (and, for "both", emulators too) come first: what's installed decides what gets scanned.
    if (usage != Usage.EMULATION) add(StepId.PC_LIBRARIES)
    if (usage == Usage.EMULATION) add(StepId.EMULATORS)
    if (usage != Usage.GAMES) { add(StepId.FOLDERS); add(StepId.CORES); add(StepId.BIOS) }
    add(StepId.ARTWORK); add(StepId.DONE)
}

/** A focusable thing on the current screen; the screen's buttons come last. */
private class Target(val onActivate: () -> Unit)

/** Collected while composing a step, so the controller handler knows what's focusable. */
private class StepScope(val focus: Int, val big: Boolean) {
    /**
     * Keyed, in first-registration order. A control's own animation can recompose just that control,
     * which registers it again — the key keeps its position stable (appending would shift indices).
     */
    private val byKey = LinkedHashMap<String, Target>()
    val targets: List<Target> get() = byKey.values.toList()
    val choices: Int get() = byKey.keys.count { it.startsWith("choice:") }
    var primaryIndex = -1
    fun register(key: String, onActivate: () -> Unit): Int {
        byKey[key] = Target(onActivate)   // replacing keeps insertion order
        return byKey.keys.indexOf(key)
    }
    /** Before the user moves (focus = -1): the first choice, or the primary button when there are no choices. */
    fun isFocused(i: Int, isChoice: Boolean = false, isPrimary: Boolean = false) =
        i == focus || (focus == -1 && ((isChoice && i == 0) || (isPrimary && choices == 0)))
    fun effectiveFocus() = if (focus != -1) focus else if (choices > 0) 0 else primaryIndex.coerceAtLeast(0)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Onboarding() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val pads by app.controllers.controllers.collectAsState()
    val fullscreen by app.fullscreen.collectAsState()
    val family by app.controllers.activeFamily.collectAsState()
    val big = fullscreen

    val nintendoPad = pads.any { it.family == ControllerFamily.NINTENDO }
    val list = steps(settings, nintendoPad)
    val current = settings.onboardingStep?.let { id -> StepId.entries.firstOrNull { it.name == id } }?.takeIf { it in list } ?: StepId.WELCOME
    val index = list.indexOf(current)
    var focus by remember(current) { mutableIntStateOf(-1) }
    var pressPulse by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }

    fun goTo(step: StepId, dir: Int) { direction = dir; app.updateSettings { it.copy(onboardingStep = step.name) } }
    fun next() { list.getOrNull(index + 1)?.let { goTo(it, 1) } }
    fun back() { list.getOrNull(index - 1)?.let { goTo(it, -1) } }
    fun finish() {
        app.updateSettings { it.copy(onboardingComplete = true, onboardingStep = null) }
        app.mode.value = app.settings.value.defaultMode
        if (app.settings.value.defaultMode != UiMode.LIST) app.fullscreen.value = true
        app.rescan()
    }

    // Targets registered by the visible step (rebuilt every composition; a plain holder, not state).
    val scopeHolder = remember { arrayOf(StepScope(0, big)) }
    val latestFocus by rememberUpdatedState(focus)

    fun handle(nav: Nav) {
        val sc = scopeHolder[0]
        val t = sc.targets
        val f = if (latestFocus == -1) sc.effectiveFocus() else latestFocus
        when (nav) {
            Nav.LEFT, Nav.UP, Nav.PREV_TAB -> focus = (f - 1).coerceAtLeast(0)
            Nav.RIGHT, Nav.DOWN, Nav.NEXT_TAB -> focus = (f + 1).coerceAtMost((t.size - 1).coerceAtLeast(0))
            Nav.CONFIRM -> t.getOrNull(f)?.onActivate?.invoke()
            Nav.BACK -> back()
            Nav.MENU -> t.lastOrNull()?.onActivate?.invoke()   // Start = the primary (last) button
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            if (e.nav == Nav.CONFIRM) { pressPulse++; delay(PRESS_DELAY_MS) }
            handle(e.nav)
        }
    }

    val pad = if (big) 72.dp else 48.dp
    CompositionLocalProvider(LocalPressPulse provides pressPulse) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        ) {
            // Progress: a quiet wavy line, not a list of steps.
            if (current != StepId.WELCOME) {
                LinearWavyProgressIndicator(progress = { index / (list.size - 1f) },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = pad, vertical = 24.dp))
            }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(current, Modifier.fillMaxSize(), transitionSpec = {
                (slideInHorizontally(motion.defaultSpatialSpec()) { it / 5 * direction } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(motion.fastSpatialSpec()) { -it / 5 * direction } + fadeOut(motion.fastEffectsSpec()))
            }) { step ->
                val stepScope = StepScope(if (step == current) focus else -1, big)
                if (step == current) scopeHolder[0] = stepScope
                when (step) {
                    StepId.WELCOME -> Welcome(stepScope, ::next)
                    StepId.USAGE -> UsageStep(stepScope, settings, ::back, ::next)
                    StepId.LOOK -> Look(stepScope, settings, ::back, ::next)
                    StepId.CONTROLS -> Controls(stepScope, settings, ::back, ::next)
                    StepId.NINTENDO -> NintendoLayout(stepScope, settings, ::back, ::next)
                    StepId.PC_LIBRARIES -> PcLibraries(stepScope, settings, withEmulators = settings.usage == Usage.BOTH, ::back, ::next)
                    StepId.FOLDERS -> Folders(stepScope, settings, ::back, ::next)
                    StepId.EMULATORS -> Emulators(stepScope, ::back, ::next)
                    StepId.CORES -> Cores(stepScope, ::back, ::next)
                    StepId.BIOS -> Bios(stepScope, settings, ::back, ::next)
                    StepId.ARTWORK -> Artwork(stepScope, settings, ::back, ::next)
                    StepId.DONE -> Done(stepScope, settings, ::back, ::finish)
                }
            }
            if (current != StepId.WELCOME) HintBar(
                listOf(Pad.SOUTH to "Choose", Pad.EAST to "Back", Pad.START to "Continue").map { (p, l) ->
                    (if (settings.nintendoConfirm && p == Pad.SOUTH) Pad.EAST else if (settings.nintendoConfirm && p == Pad.EAST) Pad.SOUTH else p) to l
                },
                family, Modifier.align(Alignment.BottomStart).padding(start = pad, bottom = 28.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- building blocks

private val StepScope.titleStyle @Composable get() = if (big) MaterialTheme.typography.displayMediumEmphasized else MaterialTheme.typography.displaySmallEmphasized

/** Standard question layout: big title, one-line explanation, content, then Back / Continue. */
@Composable
private fun StepScope.Question(
    title: String, subtitle: String,
    onBack: (() -> Unit)?, onContinue: () -> Unit, continueLabel: String = "Continue", continueEnabled: Boolean = true,
    content: @Composable StepScope.() -> Unit,
) {
    val pad = if (big) 72.dp else 48.dp
    Column(Modifier.fillMaxSize().padding(start = pad, end = pad, top = if (big) 72.dp else 56.dp, bottom = if (big) 40.dp else 28.dp)) {
        // Title, explanation and choices as one block, centred both ways.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                Text(subtitle, style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp, bottom = if (big) 48.dp else 36.dp).widthIn(max = 820.dp))
                content()
            }
        }
        // Buttons registered last so they're the final focus targets (Start = Continue).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) PillButton("Back", primary = false, onClick = onBack)
            PillButton(continueLabel, primary = true, enabled = continueEnabled, onClick = onContinue)
        }
    }
}

/** The one button style used throughout setup. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepScope.PillButton(label: String, primary: Boolean, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val i = register("button:$label") { if (enabled) onClick() }
    if (primary) primaryIndex = i
    val focused = isFocused(i, isPrimary = primary)
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(when {
        !enabled -> cs.surfaceContainerHighest
        primary -> cs.primary
        focused -> cs.secondaryContainer
        else -> cs.surfaceContainerHigh
    }, MaterialTheme.motionScheme.fastEffectsSpec())
    val fg = when { !enabled -> cs.onSurface.copy(alpha = 0.38f); primary -> cs.onPrimary; focused -> cs.onSecondaryContainer; else -> cs.onSurface }
    val corner by animateDpAsState(if (focused) 18.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    val ring by animateColorAsState(if (focused) cs.onSurface else Color.Transparent, MaterialTheme.motionScheme.fastEffectsSpec())
    val interaction = remember { MutableInteractionSource() }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(corner), color = bg, interactionSource = interaction,
        modifier = Modifier.height(if (big) 72.dp else 56.dp).focusProperties { canFocus = false }.pressBounce(LocalPressPulse.current, focused, interaction)
            .border(3.dp, ring, RoundedCornerShape(corner + 4.dp))) {
        Row(Modifier.padding(horizontal = if (big) 40.dp else 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, null, tint = fg)
            Text(label, color = fg, style = (if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .reactive(focused) { copy(wght = 720f, wdth = 108f, rond = 100f) })
        }
    }
}

/** Large choice card: icon (or brand icon), title, one line. Selected = tonal fill + check; focused = outline. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepScope.Choice(
    title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit,
    icon: ImageVector? = null, tile: BrandTile? = null, preview: (@Composable (Boolean) -> Unit)? = null,
) {
    val i = register("choice:$title", onClick)
    val focused = isFocused(i, isChoice = true)
    val cs = MaterialTheme.colorScheme
    val w = if (big) CARD_W_BIG else CARD_W
    val h = if (big) CARD_H_BIG else CARD_H
    val iconSize = if (big) 88.dp else 72.dp
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surfaceContainerHigh, MaterialTheme.motionScheme.defaultEffectsSpec())
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
    val corner by animateDpAsState(if (selected) 36.dp else 24.dp, MaterialTheme.motionScheme.defaultSpatialSpec())
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
    val ring by animateColorAsState(if (focused) cs.primary else Color.Transparent, MaterialTheme.motionScheme.fastEffectsSpec())
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(w, h).graphicsLayer { scaleX = scale; scaleY = scale }.pressBounce(LocalPressPulse.current, focused, interaction)
            .border(3.dp, ring, RoundedCornerShape(corner + 4.dp)).padding(5.dp)
            .clip(RoundedCornerShape(corner)).background(bg)
            .focusProperties { canFocus = false }   // focus is ours (controller/keyboard nav), not Compose's
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(if (big) 28.dp else 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Every card's icon is the same rounded tile: the brand tiles as designed, or a tonal tile
            // with the same corner ratio (112/512) holding a Material icon.
            when {
                preview != null -> Box(Modifier.fillMaxWidth().height(if (big) 120.dp else 104.dp).clip(RoundedCornerShape(16.dp))
                    .background(cs.surfaceContainerLowest)) { preview(selected) }
                // Hairline edge so dark tiles (play stack) still read as a full square on dark cards.
                tile != null -> BrandTileImage(tile, Modifier.size(iconSize)
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(iconSize * 0.22f)))
                icon != null -> Box(Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.22f))
                    .background(if (selected) cs.primary else cs.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(iconSize * 0.5f), tint = if (selected) cs.onPrimary else cs.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(title, color = fg, style = (if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                .reactive(selected || focused) { copy(wght = 740f, rond = 100f) }, maxLines = 2)
            subtitle?.let { Text(it, color = fg.copy(alpha = 0.8f), style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).size(28.dp), tint = cs.onPrimaryContainer)
    }
}

private val CARD_W = 260.dp
private val CARD_H = 280.dp
private val CARD_W_BIG = 300.dp
private val CARD_H_BIG = 330.dp

/** Cards centred, wrapping onto a new centred line on narrow windows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(content: @Composable () -> Unit) =
    // Padding leaves room for the focused card's lift and outline (otherwise clipped by the scroll area).
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(20.dp)) { content() }

// ---------------------------------------------------------------- steps

/** A pause measured on the frame clock (deterministic, unlike delay()). */
private suspend fun framePause(ms: Long) {
    val start = withFrameMillis { it }
    while (withFrameMillis { it } - start < ms) { /* wait a frame */ }
}

/** Continues the launch animation: the logo rises and "Welcome" + Continue appear. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Welcome(s: StepScope, next: () -> Unit) {
    // Sequence on the frame clock: let the launch animation fade, glide the logo up, then bring in
    // "Welcome" + Continue (rising and fading in, no overshoot on the fade).
    val splashActive = LocalSplashActive.current
    val rise = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(splashActive) {
        if (splashActive) return@LaunchedEffect
        framePause(220)                 // the launch animation's fade-out
        rise.animateTo(1f, spatial)     // logo glides up…
        appear.animateTo(1f, effects)   // …then the welcome and button arrive
    }
    Box(Modifier.fillMaxSize()) {
        WordmarkLockup(Modifier.align(Alignment.Center).offset(y = (-110).dp * rise.value))
        Column(Modifier.align(Alignment.Center).offset(y = 70.dp + 24.dp * (1 - appear.value)).alpha(appear.value.coerceIn(0f, 1f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Text("Welcome", style = MaterialTheme.typography.displaySmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
            with(s) { PillButton("Continue", primary = true, onClick = next) }
        }
    }
}

@Composable
private fun UsageStep(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    fun pick(u: Usage) = app.updateSettings { it.copy(usage = u, importSteam = u != Usage.EMULATION, importLaunchers = u != Usage.EMULATION) }
    Question("What will you use it for?", "We'll only ask about what you need. You can change everything later in Settings.",
        back, next, continueEnabled = settings.usage != null) {
        ChoiceRow {
            Choice("PC games", "Steam, Epic, GOG, Xbox and other launchers in one library", settings.usage == Usage.GAMES, { pick(Usage.GAMES) }, tile = BrandTile.PLAY_STACK)
            Choice("Emulation", "Retro and modern consoles, with emulators set up for you", settings.usage == Usage.EMULATION, { pick(Usage.EMULATION) }, tile = BrandTile.CONTROLLER)
            Choice("Both", "Everything in one place", settings.usage == Usage.BOTH, { pick(Usage.BOTH) }, tile = BrandTile.CARTRIDGE)
        }
    }
}

@Composable
private fun Look(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    fun pick(m: ThemeMode) = app.updateSettings { it.copy(themeMode = m) }
    Question("Light or dark?", "Colours also follow the artwork of the game you're on. Accent colours are in Settings.", back, next) {
        ChoiceRow {
            Choice("Automatic", "Follow your system", settings.themeMode == ThemeMode.SYSTEM, { pick(ThemeMode.SYSTEM) }, Icons.Outlined.BrightnessAuto)
            Choice("Light", null, settings.themeMode == ThemeMode.LIGHT, { pick(ThemeMode.LIGHT) }, Icons.Outlined.LightMode)
            Choice("Dark", null, settings.themeMode == ThemeMode.DARK, { pick(ThemeMode.DARK) }, Icons.Outlined.DarkMode)
            Choice("Black", "For OLED screens", settings.themeMode == ThemeMode.AMOLED, { pick(ThemeMode.AMOLED) }, Icons.Outlined.Contrast)
        }
    }
}

@Composable
private fun Controls(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    val pads by app.controllers.controllers.collectAsState()
    fun pick(m: UiMode) = app.updateSettings { it.copy(defaultMode = m, bigScreenOnController = true, startFullscreen = m != UiMode.LIST) }
    val connected = pads.firstOrNull()
    Question("Pick a theme", connected?.let { "${it.typeLabel} connected — Big Screen and XMB are made for it. You can switch any time." }
        ?: "How the app opens. You can switch between them any time.", back, next) {
        ChoiceRow {
            Choice("Library", "A grid with sorting and search, made for keyboard and mouse", settings.defaultMode == UiMode.LIST,
                { pick(UiMode.LIST) }, preview = { LibrarySkeleton(it) })
            Choice("Big Screen", "Shelves of games and big art, made for a controller on the couch", settings.defaultMode == UiMode.BIGSCREEN,
                { pick(UiMode.BIGSCREEN) }, preview = { BigScreenSkeleton(it) })
            Choice("XMB", "Categories across, games down — the classic console menu", settings.defaultMode == UiMode.XMB,
                { pick(UiMode.XMB) }, preview = { XmbSkeleton(it) })
        }
        if (connected != null) Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Pad.SOUTH, Pad.EAST, Pad.WEST, Pad.NORTH).forEach { ButtonGlyph(it, connected.family) }
            Text(connected.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Skeleton previews: blank tiles and round pills where titles would be ----

@Composable private fun skeletonTile(selected: Boolean) = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerHighest
@Composable private fun skeletonPill(selected: Boolean) = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

@Composable
private fun Pill(w: Dp, selected: Boolean, h: Dp = 5.dp) = Box(Modifier.size(w, h).clip(CircleShape).background(skeletonPill(selected)))

@Composable
private fun Tile(w: Dp, h: Dp, selected: Boolean, r: Dp = 4.dp) = Box(Modifier.size(w, h).clip(RoundedCornerShape(r)).background(skeletonTile(selected)))

@Composable
private fun LibrarySkeleton(sel: Boolean) = Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(Modifier.width(36.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(5) { Pill(if (it == 0) 26.dp else 20.dp, sel && it == 0) } }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Pill(70.dp, sel, 6.dp)
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Tile(22.dp, 28.dp, sel); Pill(18.dp, sel, 3.dp) } }
            }
        }
    }
}

@Composable
private fun BigScreenSkeleton(sel: Boolean) = Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Pill(90.dp, sel, 9.dp); Pill(60.dp, sel); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.size(34.dp, 46.dp).border(2.dp, if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
            .padding(3.dp)) { Tile(28.dp, 40.dp, sel, 5.dp) }
        repeat(4) { Tile(28.dp, 38.dp, sel, 5.dp) }
    }
}

@Composable
private fun XmbSkeleton(sel: Boolean) = Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i -> Box(Modifier.size(if (i == 1) 20.dp else 14.dp).clip(CircleShape).background(if (i == 1) skeletonTile(sel) else MaterialTheme.colorScheme.surfaceContainerHighest)) }
    }
    Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(if (i == 0) 20.dp else 14.dp, if (i == 0) 20.dp else 14.dp, sel && i == 0)
                Pill(if (i == 0) 56.dp else 40.dp, sel && i == 0, if (i == 0) 6.dp else 4.dp)
            }
        }
    }
}

@Composable
private fun NintendoLayout(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    Question("Which button confirms?", "Nintendo controllers usually confirm with the right button.", back, next) {
        ChoiceRow {
            Choice("Right button (A)", "Nintendo style", settings.nintendoConfirm, { app.updateSettings { it.copy(nintendoConfirm = true) } }, Icons.Outlined.Gamepad)
            Choice("Bottom button (B)", "Xbox / PlayStation style", !settings.nintendoConfirm, { app.updateSettings { it.copy(nintendoConfirm = false) } }, Icons.Outlined.VideogameAsset)
        }
    }
}

@Composable
private fun PcLibraries(s: StepScope, settings: Settings, withEmulators: Boolean, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    // Games per launcher; only launchers that are actually installed (have games) are offered.
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var detecting by remember { mutableStateOf(withEmulators && settings.emulators.isEmpty()) }
    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) {
            buildMap {
                put("steam", runCatching { SteamLibrary().let { it.scan().size + it.shortcuts().size } }.getOrDefault(0))
                LauncherSources.Source.entries.forEach { put(it.id, LauncherSources.scan(it).size) }
            }
        }
        // Everything shown here has been asked about, so later rescans only offer new ones.
        val shown = counts.orEmpty().filterValues { it > 0 }.keys
        app.updateSettings { it.copy(knownLaunchers = it.knownLaunchers.orEmpty() + shown) }
    }
    LaunchedEffect(Unit) {
        if (!detecting) return@LaunchedEffect
        val found = app.detectEmulators()
        app.updateSettings { st ->
            val existing = st.emulators.map { it.path }.toSet()
            st.copy(emulators = st.emulators + found.filter { it.path !in existing }.map { it.toConfig() })
        }
        detecting = false
    }
    fun games(n: Int) = if (n == 1) "1 game" else "$n games"
    val loading = counts == null || detecting
    val launchers = counts.orEmpty().filterValues { it > 0 }
    val emulatorNames = if (withEmulators) settings.emulators.map { it.name }.distinct() else emptyList()
    val nothing = !loading && launchers.isEmpty() && emulatorNames.isEmpty()
    Question(
        when { loading -> "Looking for your launchers…"; withEmulators -> "Your launchers and emulators"; else -> "Your launchers" },
        when {
            loading -> "Checking what's installed" + if (HostOs.current == HostOs.MAC) ", including CrossOver and Parallels." else "."
            nothing -> "Nothing found yet. You can add launchers and emulators later in Settings."
            else -> "Pick the ones you want to use. Their games appear with artwork and launch the usual way."
        }, back, next) {
        if (loading) CartridgeLoader(Modifier.size(if (big) 120.dp else 96.dp))
        else ChoiceRow {
            launchers.forEach { (id, n) ->
                if (id == "steam") Choice("Steam", games(n), settings.importSteam, { app.updateSettings { it.copy(importSteam = !it.importSteam) } }, Icons.Outlined.Storefront)
                else {
                    val src = LauncherSources.Source.entries.first { it.id == id }
                    val on = settings.importLaunchers && id !in settings.disabledLaunchers
                    Choice(src.label, games(n), on, {
                        app.updateSettings { st ->
                            val nowOn = st.importLaunchers && id !in st.disabledLaunchers
                            st.copy(importLaunchers = true, disabledLaunchers = if (nowOn) st.disabledLaunchers + id else st.disabledLaunchers - id)
                        }
                    }, Icons.Outlined.Widgets)
                }
            }
            emulatorNames.forEach { name ->
                val on = settings.emulators.any { it.name == name && it.enabled }
                Choice(name, "Emulator", on, {
                    app.updateSettings { st -> st.copy(emulators = st.emulators.map { if (it.name == name) it.copy(enabled = !on) else it }) }
                }, Icons.Outlined.SportsEsports)
            }
        }
    }
}

/** Common ROM locations (ES-DE, EmuDeck, RetroArch…), offered as one-tap choices. */
private fun suggestedFolders(): List<File> {
    val h = System.getProperty("user.home")
    val list = mutableListOf("$h/ROMs", "$h/roms", "$h/Emulation/roms", "$h/Games/ROMs", "$h/RetroArch/roms", "$h/Documents/ROMs", "$h/ES-DE/ROMs")
    if (HostOs.current == HostOs.WINDOWS) list += listOf("C:\\ROMs", "D:\\ROMs", "D:\\Emulation\\roms", "E:\\ROMs")
    return list.map(::File).filter { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }.distinctBy { it.canonicalPath }
}

@Composable
private fun Folders(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    val suggestions = remember { suggestedFolders() }
    val shown = (suggestions.map { it.path } + settings.romFolders.map { it.path }).distinct()
    fun toggle(path: String) = app.updateSettings { st ->
        if (st.romFolders.any { it.path == path }) st.copy(romFolders = st.romFolders.filterNot { it.path == path })
        else st.copy(romFolders = st.romFolders + RomFolder(path))
    }
    Question("Where are your games?", "Pick the folders that hold your games. Sub-folders named after systems (snes, psx, switch…) are sorted automatically.",
        back, next) {
        ChoiceRow {
            shown.take(4).forEach { path ->
                Choice(File(path).name, path, settings.romFolders.any { it.path == path }, { toggle(path) }, Icons.Outlined.Folder)
            }
            Choice("Add a folder", "Choose any folder", false, {
                FilePicker.pickFolder("Add game folder")?.let { dir -> if (settings.romFolders.none { it.path == dir.path }) toggle(dir.path) }
            }, Icons.Outlined.CreateNewFolder)
        }
        if (shown.size > 4) Text("+${shown.size - 4} more in Settings → Library", Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Emulators(s: StepScope, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var runs by remember { mutableIntStateOf(0) }
    LaunchedEffect(runs) {
        if (runs == 0 && settings.emulators.isNotEmpty()) return@LaunchedEffect
        scanning = true
        val found = app.detectEmulators()
        app.updateSettings { st ->
            val existing = st.emulators.map { it.path }.toSet()
            st.copy(emulators = st.emulators + found.filter { it.path !in existing }.map { it.toConfig() })
        }
        scanning = false
    }
    val names = settings.emulators.map { it.name }.distinct()
    Question(if (scanning) "Looking for emulators…" else if (names.isEmpty()) "No emulators found yet" else "Found ${names.size} emulator${if (names.size == 1) "" else "s"}",
        if (scanning) "Checking the usual install locations." else if (names.isEmpty()) "That's fine — retro systems run inside the app. You can add emulators later in Settings."
        else "They'll be used for systems that need them. You can change any of this in Settings.", back, next) {
        if (scanning) CartridgeLoader(Modifier.size(if (big) 120.dp else 96.dp))
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            names.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { n ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(n, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row { PillButton("Scan again", primary = false, icon = Icons.Outlined.Search) { runs++ } }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Cores(s: StepScope, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    val progress by app.coreInstall.collectAsState()
    val missing = remember(progress?.finished) { recommendedCores().filter { !CoreCatalog.isInstalled(it.id) } }
    val sizeMb = missing.sumOf { it.sizeMb }
    val supported = CoreCatalog.downloadUrl("mesen") != null
    val p = progress
    Question("Set up retro systems?", "Cores run retro games inside the app — with save states, rewind and controller menus.", back, next,
        continueLabel = if (p != null && !p.finished) "Continue (installs in background)" else "Continue") {
        when {
            p != null && !p.finished -> Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Installing ${p.current ?: ""} · ${p.done + 1} of ${p.total}", style = MaterialTheme.typography.titleLarge)
                LinearWavyProgressIndicator(progress = { (p.done + p.fraction) / p.total }, modifier = Modifier.fillMaxWidth())
            }
            missing.isEmpty() -> Choice("All set", "The recommended cores are installed", true, {}, Icons.Outlined.CheckCircle)
            !supported -> Text("There are no downloadable cores for this computer. You can add them manually later.", style = MaterialTheme.typography.titleMedium)
            else -> ChoiceRow {
                Choice("Install recommended", "${missing.size} cores · about $sizeMb MB", false, { app.installCores(missing.map { it.id }) }, Icons.Outlined.Download)
                Choice("Not now", "Install from Settings → Cores any time", false, next, Icons.Outlined.SkipNext)
            }
        }
    }
}

@Composable
private fun Bios(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    val dir = File(settings.biosDir ?: AppPaths.system.path)
    val played by produceState<Set<String>?>(null, settings.romFolders) { value = app.playedPlatforms() }
    // Only the systems found in your game folders.
    val required = BiosCatalog.all.filter { it.required && played?.contains(it.platformId) == true }
    val ready = remember(dir, required) { required.count { BiosCatalog.status(dir, it) != BiosCatalog.Status.MISSING } }
    val systems = required.map { cc.xdan.frontend.model.Platforms.get(it.platformId).shortName }.distinct()
    val explain = when {
        played == null -> "Checking which of your systems need BIOS files…"
        required.isEmpty() -> "None of the systems in your game folders need BIOS files. You're all set here."
        else -> "${systems.joinToString()} need BIOS files that you dump yourself — they're never downloaded. $ready of ${required.size} found in ${dir.path}."
    }
    Question("BIOS files", explain, back, next) {
        if (played != null && required.isEmpty()) return@Question
        ChoiceRow {
            Choice("Choose BIOS folder", "Point to where your BIOS files are", settings.biosDir != null, {
                FilePicker.pickFolder("BIOS folder")?.let { d -> app.updateSettings { it.copy(biosDir = d.path) } }
            }, Icons.Outlined.Memory)
            Choice("Skip for now", "Most systems don't need one", false, next, Icons.Outlined.SkipNext)
        }
    }
}

@Composable
private fun Artwork(s: StepScope, settings: Settings, back: () -> Unit, next: () -> Unit) = with(s) {
    val app = LocalApp.current
    var addKey by remember { mutableStateOf(settings.scraper.steamGridDbKey.isNotBlank()) }
    Question("Artwork", "Box art and Steam art work out of the box. A free SteamGridDB key adds logos and wide hero images.", back, next) {
        ChoiceRow {
            Choice("Free sources", "No account needed", !addKey, { addKey = false }, Icons.Outlined.ImageSearch)
            Choice("Add SteamGridDB", "Paste your API key", addKey, { addKey = true }, Icons.Outlined.AutoAwesome)
        }
        if (addKey) OutlinedTextField(settings.scraper.steamGridDbKey, { v -> app.updateSettings { it.copy(scraper = it.scraper.copy(steamGridDbKey = v.trim())) } },
            label = { Text("SteamGridDB API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text("steamgriddb.com → Preferences → API") },
            modifier = Modifier.padding(top = 24.dp).widthIn(min = 420.dp, max = 640.dp))
    }
}

@Composable
private fun Done(s: StepScope, settings: Settings, back: () -> Unit, finish: () -> Unit) = with(s) {
    val usage = when (settings.usage) { Usage.GAMES -> "your PC games"; Usage.EMULATION -> "your emulated games"; else -> "all your games" }
    val start = when (settings.defaultMode) { UiMode.LIST -> "the Library view"; UiMode.BIGSCREEN -> "Big Screen"; UiMode.XMB -> "XMB" }
    Question("You're all set", "We'll gather $usage and fetch artwork in the background. You'll start in $start.",
        back, finish, continueLabel = "Let's play") {
        BrandMorph(BrandIcons.cartridge, Modifier.size(if (big) 220.dp else 180.dp))
    }
}
