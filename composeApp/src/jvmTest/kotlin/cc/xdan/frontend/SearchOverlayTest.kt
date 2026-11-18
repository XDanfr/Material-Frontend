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
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlin.test.assertNull

/** Big Screen search: physical keyboard types, Options/Enter moves to results, Confirm opens one. */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class, androidx.compose.ui.InternalComposeUiApi::class)
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
        var tabSwitches = 0
        val job = kotlinx.coroutines.GlobalScope.launch { app.controllers.events.collect { if (it.nav == Nav.PREV_TAB || it.nav == Nav.NEXT_TAB) tabSwitches++ } }
        pump(60)
        press(Nav.SEARCH, Pad.NORTH); pump(30)
        assertNotNull(app.textInput, "search takes keyboard text")
        shot("bigscreen-search-empty.png")
        // Real key events through the window handler: letters that are also UI keys (Q/E/F) must type, not navigate.
        fun key(k: androidx.compose.ui.input.key.Key, ch: Char = java.awt.event.KeyEvent.CHAR_UNDEFINED) {
            val cp = if (ch == java.awt.event.KeyEvent.CHAR_UNDEFINED) 0 else ch.code
            listOf(androidx.compose.ui.input.key.KeyEventType.KeyDown, androidx.compose.ui.input.key.KeyEventType.KeyUp).forEach { t ->
                handleKey(app, androidx.compose.ui.input.key.KeyEvent(k, t, cp))
            }
            pump(2)
        }
        key(androidx.compose.ui.input.key.Key.Q, 'q'); key(androidx.compose.ui.input.key.Key.E); key(androidx.compose.ui.input.key.Key.F, 'f')
        assertEquals(0, tabSwitches, "Q/E typed instead of switching tabs")
        key(androidx.compose.ui.input.key.Key.Backspace); key(androidx.compose.ui.input.key.Key.Backspace); key(androidx.compose.ui.input.key.Key.Backspace)
        key(androidx.compose.ui.input.key.Key.M, 'm'); key(androidx.compose.ui.input.key.Key.E, 'e')
        pump(40); shot("bigscreen-search-typed.png")
        app.textInput!!('\n'); pump(10)
        press(Nav.DOWN); shot("bigscreen-search-results.png")
        press(Nav.CONFIRM, Pad.SOUTH); pump(30)
        assertNull(app.textInput, "opening a result closes search")
        job.cancel(); scene.close()
    }
}
