package app.materialfrontend.ui

import app.materialfrontend.ui.brand.BrandLoader
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
import app.materialfrontend.AppController
import app.materialfrontend.input.Nav
import app.materialfrontend.model.UiMode
import app.materialfrontend.ui.bigscreen.BigScreen
import app.materialfrontend.ui.emulation.EmulationScreen
import app.materialfrontend.ui.library.LibraryScreen
import app.materialfrontend.ui.onboarding.Onboarding
import app.materialfrontend.ui.theme.LocalApp
import app.materialfrontend.ui.theme.LocalArtworkSeed
import app.materialfrontend.ui.theme.MaterialFrontendTheme
import app.materialfrontend.ui.xmb.XmbScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(app: AppController) {
    val settings by app.settings.state.collectAsState()
    val mode by app.mode.collectAsState()
    val session by app.session.collectAsState()
    val external by app.external.collectAsState()
    val seed = remember { mutableStateOf<Color?>(null) }
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
                                        Button(onClick = { app.stopExternal() }) { Text("Close emulator") }
                                    }
                                    Text("Press Guide to close it from a controller.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (mode == UiMode.LIST) 24.dp else 96.dp))
                }
            }
        }
    }
}
