package app.materialfrontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.materialfrontend.input.Nav
import app.materialfrontend.model.Game
import app.materialfrontend.model.GameKind
import app.materialfrontend.model.GameMeta
import app.materialfrontend.model.UiMode
import app.materialfrontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Opt-in (MF_SCREENSHOTS=<dir>): renders each mode offscreen to PNG for visual review.
 * Run with MF_DATA_DIR pointing to a scratch folder so no real settings are touched.
 */
class ScreenshotTest {
    @Test
    fun renderModes() {
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File) ?: return
        out.mkdirs()
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true) }
        val titles = listOf("Super Mario World" to "snes", "Sonic the Hedgehog" to "megadrive", "Metroid Fusion" to "gba", "Castlevania" to "nes",
            "Crash Bandicoot" to "psx", "Shadow of the Colossus" to "ps2", "Zelda: Majora's Mask" to "n64", "Hades" to "steam", "Celeste" to "steam",
            "Kirby Super Star" to "snes", "Pokemon Emerald" to "gba", "Wipeout" to "psx")
        app.library.update {
            titles.mapIndexed { i, (t, p) ->
                Game("g$i", t, p, if (p == "steam") GameKind.STEAM else if (i == 6) GameKind.PORT else GameKind.ROM, "/roms/$t",
                    lastPlayed = if (i % 3 == 0) System.currentTimeMillis() - i * 3_600_000L else null, playTimeSeconds = i * 1800L, favorite = i % 4 == 0,
                    meta = GameMeta(description = "A sample description for $t.", releaseYear = 1990 + i, developer = "Studio $i", genres = listOf("Action")))
            }
        }
        fun shot(name: String, w: Int = 1440, h: Int = 900, setup: () -> Unit) {
            setup()
            val scene = ImageComposeScene(w, h, Density(1f)) { App(app, showSplash = false) }
            var t = 0L
            repeat(90) { scene.render(t); t += 16_000_000L }
            Thread.sleep(400)
            repeat(30) { scene.render(t); t += 16_000_000L }
            val img = scene.render(t)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
        shot("list") { app.mode.value = UiMode.LIST }
        shot("bigscreen") { app.mode.value = UiMode.BIGSCREEN }
        shot("xmb") { app.mode.value = UiMode.XMB }
        app.updateSettings { it.copy(onboardingComplete = false) }
        shot("onboarding") {}

        // Press feedback: confirm on Big Screen bounces the focused card, then (after the bounce) opens the game page.
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.mode.value = UiMode.BIGSCREEN
        val press = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var pt = 0L
        repeat(60) { press.render(pt); pt += 16_000_000L }
        app.controllers.onKey(Nav.CONFIRM, null, true)
        repeat(50) { press.render(pt); pt += 16_000_000L; Thread.sleep(8) }
        File(out, "press-details.png").writeBytes(press.render(pt).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        press.close()

        // Launch animation at key moments of its timeline (frame-clock driven).
        app.updateSettings { it.copy(onboardingComplete = true) }
        val splash = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = true) }
        var t = 0L
        for ((name, ms) in listOf("splash-1-circle" to 200L, "splash-2-cookie" to 640L, "splash-3-flower" to 1150L,
                                  "splash-4-wave" to 1650L, "splash-5-final" to 2500L)) {
            while (t < ms * 1_000_000L) { splash.render(t); t += 16_000_000L }
            File(out, "$name.png").writeBytes(splash.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        splash.close()
    }
}
