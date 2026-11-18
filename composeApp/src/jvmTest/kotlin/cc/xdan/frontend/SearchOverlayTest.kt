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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Big Screen search: physical keyboard types, Options/Enter moves to results, Confirm opens one. */
class SearchOverlayTest {
    @Test fun keyboardTypingAndResults() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true, bigScreenHints = true) }
        app.library.update { listOf("Zelda", "Mario", "Metroid").mapIndexed { i, t -> Game("s$i", t, "snes", path = "/s/$i") } }
        app.mode.value = UiMode.BIGSCREEN
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav, pad: Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }
        fun shot(n: String) = out?.let { File(it, n).writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        pump(60)
        press(Nav.SEARCH, Pad.NORTH); pump(30)
        assertNotNull(app.textInput, "search takes keyboard text")
        shot("bigscreen-search-empty.png")
        "me".forEach { c -> app.textInput!!(c); pump(2) }
        pump(40); shot("bigscreen-search-typed.png")
        app.textInput!!('\n'); pump(10)
        press(Nav.DOWN); shot("bigscreen-search-results.png")
        press(Nav.CONFIRM, Pad.SOUTH); pump(30)
        assertNull(app.textInput, "opening a result closes search")
        scene.close()
    }
}
