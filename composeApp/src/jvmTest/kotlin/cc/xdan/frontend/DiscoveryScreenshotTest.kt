package cc.xdan.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.emulators.DetectedEmulator
import cc.xdan.frontend.emulators.EmulatorCatalog
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.ui.components.CoverArt
import cc.xdan.frontend.ui.components.DiscoveryDialog
import cc.xdan.frontend.ui.components.LocalRunnable
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.LocalArtworkSeed
import cc.xdan.frontend.ui.theme.MaterialFrontendTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class DiscoveryScreenshotTest {
    @Test fun renders() {
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File) ?: return
        out.mkdirs()
        val app = AppController()
        val items = listOf(
            AppController.Discovered("emu:/a", "Dolphin", "Emulator", DetectedEmulator(EmulatorCatalog.all.first { it.id == "dolphin" }, "/a", "t")),
            AppController.Discovered("launcher:epic", "Epic Games", "Launcher · 3 games"),
        )
        listOf("many" to items, "single" to items.take(1)).forEach { (name, list) ->
            val scene = ImageComposeScene(1000, 600, Density(1f)) {
                CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides remember { mutableStateOf(null) }, LocalRunnable provides setOf("snes")) {
                    MaterialFrontendTheme(app.settings.value, null) {
                        Surface(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize()) {
                                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CoverArt(Game("a", "Super Mario World", "snes", path = "a"), Modifier.size(160.dp, 220.dp))
                                    CoverArt(Game("b", "Gran Turismo 4", "ps2", path = "b"), Modifier.size(160.dp, 220.dp))
                                    CoverArt(Game("c", "Portal 2", "steam", kind = cc.xdan.frontend.model.GameKind.STEAM, path = "620", installed = false), Modifier.size(160.dp, 220.dp))
                                }
                                DiscoveryDialog(app, list)
                            }
                        }
                    }
                }
            }
            var t = 0L; repeat(30) { scene.render(t); t += 16_000_000L }
            File(out, "discovery-$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
