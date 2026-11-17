@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package cc.xdan.frontend.ui

import cc.xdan.frontend.ui.emulation.LaunchingOverlay
import cc.xdan.frontend.LaunchingState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.snap
import cc.xdan.frontend.ui.brand.LaunchSplash
import cc.xdan.frontend.ui.brand.BrandLoader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.AppController
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.bigscreen.BigScreen
import cc.xdan.frontend.ui.emulation.EmulationScreen
import cc.xdan.frontend.ui.library.LibraryScreen
import cc.xdan.frontend.ui.onboarding.Onboarding
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.LocalArtworkSeed
import cc.xdan.frontend.ui.theme.MaterialFrontendTheme
import cc.xdan.frontend.ui.xmb.XmbScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(app: AppController, showSplash: Boolean = true) {
    val settings by app.settings.state.collectAsState()
    val mode by app.mode.collectAsState()
    val session by app.session.collectAsState()
    val external by app.external.collectAsState()
    val seed = remember { mutableStateOf<Color?>(null) }
    // Decided once at start-up so toggling the setting doesn't replay it.
    var splash by remember { mutableStateOf(showSplash && settings.launchAnimation && !settings.reduceMotion) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { app.toasts.collect { snackbar.showSnackbar(it) } }

    CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides seed) {
        MaterialFrontendTheme(settings, seed.value) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize()) {
                    val motion = MaterialTheme.motionScheme
                    val s = session
                    when {
                        !settings.onboardingComplete -> Onboarding()
                        s != null -> EmulationScreen(s)
                        else -> AnimatedContent(mode, transitionSpec = {
                            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f))
                                .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.02f))
                        }) { m ->
                            when (m) {
                                UiMode.LIST -> LibraryScreen()
                                UiMode.BIGSCREEN -> BigScreen()
                                UiMode.XMB -> XmbScreen()
                            }
                        }
                    }

                    // External emulator running: a calm "now playing" card with a way back.
                    AnimatedVisibility(external != null, enter = fadeIn(), exit = fadeOut()) {
                        val run = external ?: return@AnimatedVisibility
                        LaunchedEffect(run) {
                            app.controllers.events.collect { e -> if (e.nav == Nav.GUIDE) app.stopExternal() }
                        }
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    BrandLoader(Modifier.size(112.dp))
                                    Text(run.game.title, style = MaterialTheme.typography.headlineSmallEmphasized)
                                    Text("Running in ${run.label}. Play time is tracked when it closes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(shapes = ButtonDefaults.shapes(), onClick = { app.stopExternal() }) { Text("Close emulator") }
                                    }
                                    Text("Press Guide to close it from a controller.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    // Big Screen / XMB launch screen until the title is up (keeps the last state for the fade-out).
                    val launching by app.launching.collectAsState()
                    val lastLaunch = remember { mutableStateOf<LaunchingState?>(null) }
                    launching?.let { lastLaunch.value = it }
                    AnimatedVisibility(launching != null, enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec())) {
                        lastLaunch.value?.let { LaunchingOverlay(it) }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (mode == UiMode.LIST) 24.dp else 96.dp))

                    // Launch animation over the (already composing) app; fades and lifts away when done.
                    AnimatedVisibility(splash, enter = fadeIn(snap()),
                        exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.slowSpatialSpec(), targetScale = 1.04f)) {
                        LaunchSplash(onDone = { splash = false })
                    }
                }
            }
        }
    }
}
