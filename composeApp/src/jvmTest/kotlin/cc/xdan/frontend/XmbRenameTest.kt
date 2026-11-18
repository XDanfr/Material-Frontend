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
import kotlin.test.assertNotNull

/** XMB: Options → Rename opens the on-screen keyboard; a physical keyboard types into it and Enter saves. */
class XmbRenameTest {
    @Test fun renameFromOptions() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.library.update { listOf(Game("x0", "Old", "snes", path = "/x/0")) }
        app.mode.value = UiMode.XMB
        val scene = ImageComposeScene(1440, 900, Density(1f), coroutineContext = TestFrames) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.renderTolerant(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav, pad: Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        pump(60)
        press(Nav.SEARCH, Pad.NORTH)
        repeat(5) { press(Nav.DOWN) }
        press(Nav.CONFIRM, Pad.SOUTH); pump(30)
        assertNotNull(app.textInput, "rename keyboard takes typing")
        repeat(3) { app.textInput!!('\b'); pump(2) }
        "New".forEach { app.textInput!!(it); pump(2) }
        System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }?.let { File(it, "xmb-rename.png").writeBytes(scene.renderTolerant(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        app.textInput!!('\n'); pump(30)
        assertEquals("New", app.library.value.single().title)
        scene.close()
    }
}
