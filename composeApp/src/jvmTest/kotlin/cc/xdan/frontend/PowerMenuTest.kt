package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Power menu in Big Screen (button after the tabs) and XMB (last category): fullscreen toggles, restart asks first. */
class PowerMenuTest {
    @Test fun commands() {
        assertEquals("systemctl", systemPowerCommand(AppController.Power.SLEEP, HostOs.LINUX)[0])
        assertTrue(systemPowerCommand(AppController.Power.SHUT_DOWN, HostOs.MAC).last().endsWith("shut down"))
        assertEquals(listOf("shutdown", "/r", "/t", "0"), systemPowerCommand(AppController.Power.RESTART, HostOs.WINDOWS))
    }

    private fun drive(mode: UiMode, shot: String, steps: (press: (Nav, Pad?) -> Unit) -> Unit): AppController {
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.library.update { listOf(Game("p0", "Game", "snes", path = "/p/0")) }
        app.mode.value = mode
        app.fullscreen.value = false
        val scene = ImageComposeScene(1440, 900, Density(1f), coroutineContext = TestFrames) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.renderTolerant(t); t += 16_000_000L; Thread.sleep(3) }
        pump(60)
        steps { nav, pad -> app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }?.let { File(it, shot).writeBytes(scene.renderTolerant(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        scene.close()
        return app
    }

    @Test fun bigScreen() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = drive(UiMode.BIGSCREEN, "bigscreen-power.png") { press ->
            press(Nav.UP, null); repeat(4) { press(Nav.RIGHT, null) }; press(Nav.CONFIRM, Pad.SOUTH)
            // Options here: Exit, XMB, Library, Fullscreen, … → Fullscreen is index 3.
            repeat(3) { press(Nav.DOWN, null) }; press(Nav.CONFIRM, Pad.SOUTH)
        }
        assertTrue(app.fullscreen.value, "Big Screen power → Toggle fullscreen")
    }

    @Test fun xmb() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = drive(UiMode.XMB, "xmb-power.png") { press ->
            repeat(8) { press(Nav.RIGHT, null) }
            // Exit, Big Screen, Library, Fullscreen → index 3.
            repeat(3) { press(Nav.DOWN, null) }; press(Nav.CONFIRM, Pad.SOUTH)
        }
        assertTrue(app.fullscreen.value, "XMB power → Toggle fullscreen")
    }
}
