@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui.onboarding

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import cc.xdan.frontend.ui.brand.Wordmark
import cc.xdan.frontend.ui.brand.BrandArtwork
import cc.xdan.frontend.ui.theme.FlexRecipes
import cc.xdan.frontend.ui.theme.reactive
import cc.xdan.frontend.ui.brand.BrandIcons
import cc.xdan.frontend.ui.brand.BrandLoader
import cc.xdan.frontend.ui.brand.BrandMorph
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.components.MorphShape
import cc.xdan.frontend.ui.components.rememberMorph
import cc.xdan.frontend.ui.settings.BiosPanel
import cc.xdan.frontend.ui.settings.ControllersPanel
import cc.xdan.frontend.ui.settings.CoresPanel
import cc.xdan.frontend.ui.settings.EmulatorsPanel
import cc.xdan.frontend.ui.settings.GeneralPanel
import cc.xdan.frontend.ui.settings.RomFoldersPanel
import cc.xdan.frontend.ui.settings.ScraperPanel
import cc.xdan.frontend.ui.theme.LocalApp

/** Welcome, controllers, emulators, cores, games, BIOS, artwork, done. */
private val stepIcons = listOf(BrandIcons.cartridge, BrandIcons.controller, BrandIcons.dpad, BrandIcons.morphSlot,
    BrandIcons.faceButtons, BrandIcons.pixelPlay, BrandIcons.playStack, BrandIcons.cartridge)

private data class Step(val title: String, val subtitle: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Onboarding() {
    val app = LocalApp.current
    var index by remember { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            Step("Welcome", "One library for every emulator, retro and modern. Let's get you set up — everything here can be changed later in Settings.") { Welcome() },
            Step("Controllers", "Connected controllers are detected instantly. Button prompts match whatever you're holding.") { ControllersPanel() },
            Step("Emulators", "We looked in the usual places for standalone emulators. Uncheck any you don't want, or add one manually.") { EmulatorsPanel(autoScan = true) },
            Step("Cores", "Retro systems run right here in the app. The recommended set is selected — install now or skip.") { CoresPanel(preselectRecommended = true) },
            Step("Your games", "Point to your game folders. Steam games are added automatically.") { RomFoldersPanel() },
            Step("BIOS & firmware", "Some systems need BIOS files you dump yourself.") { BiosPanel() },
            Step("Artwork & metadata", "Optional accounts for richer art. You can skip this.") { ScraperPanel() },
            Step("All set", "We'll scan your folders and fetch artwork in the background.") { Finish() },
        )
    }
    val last = index == steps.lastIndex

    fun finish() {
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.mode.value = app.settings.value.defaultMode
        app.rescan()
    }

    // Controller navigation through the wizard.
    LaunchedEffect(Unit) {
        app.controllers.events.collect { e ->
            when (e.nav) {
                Nav.NEXT_TAB, Nav.MENU -> if (index < steps.lastIndex) index++ else finish()
                Nav.PREV_TAB, Nav.BACK -> if (index > 0) index--
                else -> {}
            }
        }
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        // Left: progress rail.
        Column(Modifier.width(300.dp).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Each step has its own icon from the set; changing step morphs one into the next.
            BrandMorph(stepIcons[index % stepIcons.size], Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            steps.forEachIndexed { i, s ->
                val done = i < index; val current = i == index
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = { index = i },
                ) {
                    Text(
                        (if (done) "✓  " else "${i + 1}  ") + s.title,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge.reactive(current, FlexRecipes.emphasize),
                        color = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxSize()) {
            LinearWavyProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp))
            val motion = MaterialTheme.motionScheme
            AnimatedContent(index, Modifier.weight(1f), transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(motion.defaultSpatialSpec()) { it / 6 * dir } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutHorizontally(motion.fastSpatialSpec()) { -it / 6 * dir } + fadeOut(motion.fastEffectsSpec()))
            }) { i ->
                val s = steps[i]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 8.dp).widthIn(max = 900.dp)) {
                    if (i == 0) BrandArtwork(Wordmark.HORIZONTAL, Modifier.height(96.dp).padding(bottom = 24.dp))
                    Text(s.title, style = MaterialTheme.typography.displaySmallEmphasized)
                    Text(s.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                    s.content()
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) TextButton(shapes = ButtonDefaults.shapes(), onClick = { index-- }) { Text("Back") }
                Spacer(Modifier.weight(1f))
                if (!last && index > 0) TextButton(shapes = ButtonDefaults.shapes(), onClick = { index++ }) { Text("Skip") }
                Spacer(Modifier.width(8.dp))
                Button(shapes = ButtonDefaults.shapes(), onClick = { if (last) finish() else index++ }) {
                    Text(if (last) "Scan and open library" else "Continue")
                    Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GeneralPanel()
        Text("Tip: ${if (settings.defaultMode == UiMode.LIST) "Big Screen opens automatically when you pick up a controller." else "Press Start / Menu anywhere for the quick menu."}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Finish() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BrandLoader(Modifier.size(112.dp))
        Column {
            Text("${settings.romFolders.size} folder(s) · ${settings.emulators.count { it.enabled }} emulator(s)" + if (settings.importSteam) " · Steam" else "",
                style = MaterialTheme.typography.titleLarge)
            Text("Artwork downloads continue while you browse.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
