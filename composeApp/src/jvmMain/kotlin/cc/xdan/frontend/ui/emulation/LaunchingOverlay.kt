package cc.xdan.frontend.ui.emulation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.LaunchKind
import cc.xdan.frontend.LaunchingState
import cc.xdan.frontend.libretro.EmulationSession
import cc.xdan.frontend.ui.brand.CartridgeLoader
import cc.xdan.frontend.ui.components.rememberImage
import cc.xdan.frontend.ui.theme.LocalApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

/** Never flash: keep the screen up at least this long. */
private const val MIN_SHOW_MS = 700L

/**
 * "Launching…" screen for Big Screen / XMB: the game's art, a cartridge loader and where it's
 * launching. Dismisses itself once the title is up:
 * - in-app core: first video frame (or an error, which the game screen then shows);
 * - external emulator: our window loses focus to it, or the process ends (10 s cap);
 * - Steam / launcher links: focus moves away, or after 3 s (we can't see the title otherwise).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaunchingOverlay(state: LaunchingState) {
    val app = LocalApp.current
    val windowInfo = LocalWindowInfo.current

    LaunchedEffect(state) {
        val started = System.currentTimeMillis()
        val focusLost = suspend { snapshotFlow { windowInfo.isWindowFocused }.first { !it } }
        when (state.kind) {
            LaunchKind.CORE -> withTimeoutOrNull(15_000) {
                val session = app.session.filterNotNull().first()
                session.status.first { it != EmulationSession.Status.LOADING }
                while (session.frameCounter == 0L && session.status.value == EmulationSession.Status.RUNNING) delay(16)
            }
            LaunchKind.PROCESS -> withTimeoutOrNull(10_000) {
                // Whichever comes first: the emulator's window takes focus, or the process is gone.
                merge(snapshotFlow { windowInfo.isWindowFocused }.filter { !it }.map { }, app.external.filter { it == null }.map { }).first()
            }
            LaunchKind.URI -> withTimeoutOrNull(3_000) { focusLost() }
        }
        val shown = System.currentTimeMillis() - started
        if (shown < MIN_SHOW_MS) delay(MIN_SHOW_MS - shown)
        app.launchSettled()
    }

    val g = state.game
    val backdrop by rememberImage(g.assets.hero ?: g.assets.screenshot ?: g.assets.cover, 1600)
    val logo by rememberImage(g.assets.logo, 800)
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.surface)) {
        backdrop?.let {
            Image(it, null, Modifier.fillMaxSize().alpha(0.5f).let { m -> if (g.assets.hero == null) m.blur(32.dp) else m }, contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to cs.surface.copy(alpha = 0.2f), 0.6f to cs.surface.copy(alpha = 0.85f), 1f to cs.surface)))
        Column(Modifier.align(Alignment.BottomStart).padding(64.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val l = logo
            if (l != null) Image(l, g.title, Modifier.heightIn(max = 140.dp).widthIn(max = 560.dp), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
            else Text(g.title, style = MaterialTheme.typography.displaySmallEmphasized, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CartridgeLoader(Modifier.size(80.dp))
                Text(state.via?.let { "Launching on $it…" } ?: "Launching…", style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface)
            }
        }
    }
}
