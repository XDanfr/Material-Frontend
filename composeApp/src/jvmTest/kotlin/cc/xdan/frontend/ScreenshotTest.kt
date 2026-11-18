package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.ui.emulation.LaunchingOverlay
import cc.xdan.frontend.ui.settings.SettingsScreen
import cc.xdan.frontend.ui.settings.SettingsSection
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.LocalArtworkSeed
import cc.xdan.frontend.ui.theme.MaterialFrontendTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.GameMeta
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.Usage
import cc.xdan.frontend.ui.App
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
        // Setup: one question per screen, windowed and fullscreen (controller / F11 / ⌃⌘F).
        app.updateSettings { it.copy(onboardingComplete = false, onboardingStep = null, usage = null) }
        shot("onboarding-1-welcome") {}
        shot("onboarding-2-usage") { app.updateSettings { it.copy(onboardingStep = "USAGE") } }
        shot("onboarding-2b-look") { app.updateSettings { it.copy(onboardingStep = "LOOK") } }
        shot("onboarding-2c-pc-libraries") { app.updateSettings { it.copy(onboardingStep = "PC_LIBRARIES", usage = Usage.BOTH) } }
        shot("onboarding-2d-usage-big") { app.fullscreen.value = true; app.updateSettings { it.copy(onboardingStep = "USAGE", usage = Usage.EMULATION) } }
        shot("onboarding-3-controls-big") { app.fullscreen.value = true; app.updateSettings { it.copy(onboardingStep = "CONTROLS") } }
        shot("onboarding-4-done-big") { app.updateSettings { it.copy(onboardingStep = "DONE", usage = Usage.BOTH) } }
        app.fullscreen.value = false

        // A controller can answer: Right moves to "Emulation", A chooses it.
        app.updateSettings { it.copy(onboardingStep = "USAGE", usage = null) }
        val nav = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var nt = 0L
        fun pump(n: Int) = repeat(n) { nav.render(nt); nt += 16_000_000L; Thread.sleep(4) }
        pump(30)
        app.controllers.onKey(Nav.RIGHT, null, true); pump(10)
        app.controllers.onKey(Nav.CONFIRM, null, true); pump(30)
        nav.close()
        kotlin.test.assertEquals(Usage.EMULATION, app.settings.value.usage, "controller navigation should pick Emulation")

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

        // Settings → General (shared list, accent chips) in the Library view.
        val general = ImageComposeScene(1440, 1000, Density(1f)) {
            CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides remember { mutableStateOf(null) }) {
                MaterialFrontendTheme(app.settings.value, null) { SettingsScreen(onBack = {}, initial = SettingsSection.GENERAL) }
            }
        }
        var gt = 0L
        repeat(30) { general.render(gt); gt += 16_000_000L }
        File(out, "settings-general.png").writeBytes(general.render(gt).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        general.close()

        // Big Screen → Settings tab (two tab steps right) and XMB → Settings category.
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.mode.value = UiMode.BIGSCREEN
        val bs = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var bt = 0L
        fun bpump(n: Int) = repeat(n) { bs.render(bt); bt += 16_000_000L; Thread.sleep(3) }
        bpump(30); app.controllers.onKey(Nav.NEXT_TAB, null, true); bpump(10); app.controllers.onKey(Nav.NEXT_TAB, null, true); bpump(60)
        File(out, "bigscreen-settings.png").writeBytes(bs.render(bt).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        bs.close()
        app.mode.value = UiMode.XMB
        val xs = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var xt = 0L
        fun xpump(n: Int) = repeat(n) { xs.render(xt); xt += 16_000_000L; Thread.sleep(3) }
        xpump(30); app.controllers.onKey(Nav.LEFT, null, true); xpump(60)
        File(out, "xmb-settings.png").writeBytes(xs.render(xt).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        xs.close()

        // Settings → About (credit, link, reset options).
        val about = ImageComposeScene(1440, 1100, Density(1f)) {
            CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides remember { mutableStateOf(null) }) {
                MaterialFrontendTheme(app.settings.value, null) { SettingsScreen(onBack = {}, initial = SettingsSection.ABOUT) }
            }
        }
        var at = 0L
        repeat(30) { about.render(at); at += 16_000_000L }
        File(out, "settings-about.png").writeBytes(about.render(at).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        about.close()

        // Launching screen (Big Screen / XMB) for a game launching through an emulator.
        val launching = ImageComposeScene(1440, 900, Density(1f)) {
            CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides remember { mutableStateOf(null) }) {
                MaterialFrontendTheme(app.settings.value, null) {
                    LaunchingOverlay(LaunchingState(app.library.value.first { it.platformId == "gba" }, "mGBA", LaunchKind.URI))
                }
            }
        }
        var lt = 0L
        repeat(40) { launching.render(lt); lt += 16_000_000L }
        File(out, "launching.png").writeBytes(launching.render(lt).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        launching.close()

        // Launch animation → setup hand-off: the logo should rise first, then "Welcome" + Continue appear.
        app.updateSettings { it.copy(onboardingComplete = false, onboardingStep = null) }
        val handoff = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = true) }
        var ht = 0L
        for ((name, ms) in listOf("handoff-1-logo" to 2700L, "handoff-2-rising" to 3150L, "handoff-3-risen" to 3700L, "handoff-4-button" to 4300L)) {
            while (ht < ms * 1_000_000L) { handoff.render(ht); ht += 16_000_000L }
            File(out, "$name.png").writeBytes(handoff.render(ht).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        handoff.close()
        app.updateSettings { it.copy(onboardingComplete = true) }

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
