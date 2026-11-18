package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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

/** Drives Big Screen with navigation events: Up reaches the tabs, More… hides and removes games. */
class BigScreenNavTest {
    @Test fun upReachesTabsAndMoreHidesGames() {
        System.getenv("MF_DATA_DIR") ?: return   // needs a scratch data dir (writes settings)
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true, bigScreenHints = true) }
        app.library.update { (0 until 6).map { Game("g$it", "Game $it", "snes", path = "/r/$it", lastPlayed = 1000L + it) } }
        app.mode.value = UiMode.BIGSCREEN
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav, pad: Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        pump(60)
        // Spotlight → Up → tab bar; Right switches to Library.
        press(Nav.UP); press(Nav.RIGHT); press(Nav.DOWN)
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }
        out?.let { File(it, "bigscreen-library-after-tabs.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        // Library grid (sorted by title): first game → Details → More… → Hide.
        press(Nav.CONFIRM, Pad.SOUTH)
        repeat(3) { press(Nav.RIGHT) }
        press(Nav.CONFIRM, Pad.SOUTH)
        out?.let { File(it, "bigscreen-manage.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        press(Nav.CONFIRM, Pad.SOUTH)
        val hideDeadline = System.currentTimeMillis() + 3000
        while (app.library.value.none { it.hidden } && System.currentTimeMillis() < hideDeadline) pump(5)
        assertEquals(listOf("g0"), app.library.value.filter { it.hidden }.map { it.id }, "first game hidden via More…")
        // Remove needs two presses.
        press(Nav.CONFIRM, Pad.SOUTH); repeat(3) { press(Nav.RIGHT) }; press(Nav.CONFIRM, Pad.SOUTH)
        press(Nav.DOWN); press(Nav.DOWN); press(Nav.CONFIRM, Pad.SOUTH)
        assertEquals(6, app.library.value.size, "first press only arms remove")
        press(Nav.CONFIRM, Pad.SOUTH)
        // Confirm acts after a real-time press delay; slow CI runners need a moment.
        val deadline = System.currentTimeMillis() + 3000
        while (app.library.value.size != 5 && System.currentTimeMillis() < deadline) pump(5)
        assertEquals(5, app.library.value.size)
        assertTrue(app.settings.value.removedGames.isNotEmpty(), "removal remembered for rescans")
        scene.close()
    }
}
