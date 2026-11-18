package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.XmbBackground
import cc.xdan.frontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** XMB: settings pages with a value sidebar, and the game options sidebar. */
class XmbNavTest {
    @Test fun pagesSidebarAndGameOptions() {
        System.getenv("MF_DATA_DIR") ?: return
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true, xmbBackground = XmbBackground.WAVES) }
        app.library.update { (0 until 4).map { Game("g$it", "Game $it", "snes", path = "/r/$it", lastPlayed = 1000L + it) } }
        app.mode.value = UiMode.XMB
        val scene = ImageComposeScene(1440, 900, Density(1f), coroutineContext = TestFrames) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.renderTolerant(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav, pad: Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        fun shot(name: String) = out?.let { File(it, "$name.png").writeBytes(scene.renderTolerant(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        pump(60)
        // Settings category → "Library view", "Big Screen", General, XMB (theme) page.
        press(Nav.LEFT); repeat(3) { press(Nav.DOWN) }
        press(Nav.CONFIRM, Pad.SOUTH)
        shot("xmb-page")
        press(Nav.CONFIRM, Pad.SOUTH)   // Background → sidebar
        shot("xmb-sidebar")
        press(Nav.DOWN); press(Nav.DOWN); press(Nav.CONFIRM, Pad.SOUTH)
        pump(20)
        assertEquals(XmbBackground.SQUIGGLES, app.settings.value.xmbBackground)
        // Back out, go to games; Triangle → options → "Add to Favourites".
        press(Nav.BACK, Pad.EAST); press(Nav.RIGHT)
        press(Nav.SEARCH, Pad.NORTH)
        shot("xmb-game-options")
        press(Nav.DOWN); press(Nav.CONFIRM, Pad.SOUTH); pump(20)
        assertTrue(app.library.value.any { it.favorite }, "favourited from the options sidebar")
        // Each background.
        XmbBackground.entries.forEach { b ->
            app.updateSettings { it.copy(xmbBackground = b, xmbWaves = b != XmbBackground.OFF) }
            repeat(40) { scene.renderTolerant(t); t += 250_000_000L }   // let things drift a while
            shot("xmb-bg-${b.name.lowercase()}")
        }
        scene.close()
    }
}
