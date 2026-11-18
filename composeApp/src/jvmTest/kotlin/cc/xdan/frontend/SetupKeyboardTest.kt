package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Keyboard navigation in windowed setup: focus must move and stay highlighted. Opt-in render (MF_SCREENSHOTS). */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class SetupKeyboardTest {
    @Test
    fun arrowKeysMoveAndKeepFocus() {
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File) ?: return
        if (System.getenv("MF_DATA_DIR") == null) return
        out.mkdirs()
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = false, onboardingStep = "USAGE", usage = null) }
        app.fullscreen.value = false
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var t = 0L
        fun pump(ms: Long) { val end = t + ms * 1_000_000; while (t < end) { scene.render(t); t += 16_000_000L } }
        fun shot(name: String) = File(out, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        pump(500)
        // Same path as the window: keys go through handleKey (onPreviewKeyEvent) → navigation events.
        handleKey(app, KeyEvent(Key.DirectionRight, KeyEventType.KeyDown))
        handleKey(app, KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
        pump(100); shot("setup-key-1-after-100ms")
        pump(700); shot("setup-key-2-after-800ms")
        handleKey(app, KeyEvent(Key.Enter, KeyEventType.KeyDown)); handleKey(app, KeyEvent(Key.Enter, KeyEventType.KeyUp))
        // The press bounce waits in real time before acting: keep rendering until it lands (or 3 s pass).
        val deadline = System.currentTimeMillis() + 3000
        while (app.settings.value.usage == null && System.currentTimeMillis() < deadline) { pump(16); Thread.sleep(10) }
        kotlin.test.assertEquals(cc.xdan.frontend.model.Usage.EMULATION, app.settings.value.usage, "Right then Enter should choose Emulation")
        // Continue (Tab = Start) to the next page; keys must still drive that page, not the first one.
        fun tap(k: Key) { handleKey(app, KeyEvent(k, KeyEventType.KeyDown)); handleKey(app, KeyEvent(k, KeyEventType.KeyUp)); repeat(40) { pump(16); Thread.sleep(10) } }
        tap(Key.Tab)
        val second = app.settings.value.onboardingStep
        kotlin.test.assertNotEquals("USAGE", second, "Tab continues")
        tap(Key.Tab)
        val third = app.settings.value.onboardingStep
        kotlin.test.assertNotEquals(second, third, "keys still work on the page after continuing")
        tap(Key.Escape)
        kotlin.test.assertEquals(second, app.settings.value.onboardingStep, "Back from the third page goes to the second")
        scene.close()
        app.shutdown()
    }
}
