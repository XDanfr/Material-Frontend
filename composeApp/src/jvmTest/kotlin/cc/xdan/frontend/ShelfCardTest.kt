package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameAssets
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test

/** Big Screen Home: the focused shelf card widens to its wide art, and shrinks back when focus moves on. */
class ShelfCardTest {
    private fun png(dir: File, name: String, w: Int, h: Int, color: Int): String {
        val s = Surface.makeRasterN32Premul(w, h)
        s.canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), Paint().apply { this.color = color })
        s.canvas.drawRect(Rect.makeXYWH(w * 0.1f, h * 0.6f, w * 0.5f, h * 0.15f), Paint().apply { this.color = 0xFFFFFFFF.toInt() })
        return File(dir, name).apply { writeBytes(s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes) }.path
    }

    @Test fun focusedCardExpands() {
        val dataDir = System.getenv("MF_DATA_DIR") ?: return
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() } ?: return
        val art = File(dataDir, "shelf-art").apply { mkdirs() }
        val colors = listOf(0xFF8E3B46.toInt(), 0xFF3B6E8E.toInt(), 0xFF4E8E3B.toInt(), 0xFF8E7A3B.toInt())
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true) }
        app.library.update {
            colors.mapIndexed { i, c ->
                Game("s$i", "Shelf Game $i", "steam", path = "$i", lastPlayed = 1000L - i,
                    assets = GameAssets(cover = png(art, "c$i.png", 200, 300, c), hero = if (i == 2) null else png(art, "h$i.png", 640, 360, c)))
            }
        }
        app.mode.value = UiMode.BIGSCREEN
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav) { app.controllers.onKey(nav, null, true); pump(4); app.controllers.onKey(nav, null, false); pump(40) }
        fun shot(n: String) = File(out, n).writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        pump(60)
        press(Nav.DOWN); pump(30); shot("bigscreen-shelf-expanded.png")
        press(Nav.RIGHT); pump(30); shot("bigscreen-shelf-next.png")
        press(Nav.RIGHT); pump(30); shot("bigscreen-shelf-blurred.png")   // game 2 has no wide art
        // Top centre really is centred (the hidden title used to widen the logo's box).
        app.library.updateGame("s2") { it.copy(logoPin = cc.xdan.frontend.model.LogoPin.UPPER_CENTER) }
        pump(30); shot("bigscreen-shelf-topcentre.png")
        scene.close()
    }

    @Test fun logoEditorWithController() {
        System.getenv("MF_DATA_DIR") ?: return
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() }
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true, logoPin = null) }
        app.library.update { listOf(Game("L0", "Logo Game", "steam", path = "1", defaultLogoPin = cc.xdan.frontend.model.LogoPin.BOTTOM_LEFT)) }
        app.mode.value = UiMode.BIGSCREEN
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.render(t); t += 16_000_000L; Thread.sleep(3) }
        fun press(nav: Nav, pad: cc.xdan.frontend.input.Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); pump(30) }
        pump(60)
        press(Nav.UP); press(Nav.RIGHT); press(Nav.DOWN)                 // Library grid
        press(Nav.CONFIRM, cc.xdan.frontend.input.Pad.SOUTH)              // Details
        repeat(3) { press(Nav.RIGHT) }; press(Nav.CONFIRM, cc.xdan.frontend.input.Pad.SOUTH)   // More…
        repeat(4) { press(Nav.DOWN) }; press(Nav.CONFIRM, cc.xdan.frontend.input.Pad.SOUTH)    // Adjust logo…
        press(Nav.RIGHT); press(Nav.UP)                                   // bottom left → bottom centre → centre
        press(Nav.NEXT_TAB); press(Nav.NEXT_TAB)                          // bigger, from the anchor
        out?.let { File(it, "bigscreen-logo-editor.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
        press(Nav.CONFIRM, cc.xdan.frontend.input.Pad.SOUTH); pump(20)
        val g = app.library.value.single()
        kotlin.test.assertEquals(cc.xdan.frontend.model.LogoPin.CENTER, g.logoPin)
        kotlin.test.assertEquals(0.48f, g.logoWidth!!, 0.001f)
        // A global change never moves a game you've placed yourself.
        app.updateSettings { it.copy(logoPin = cc.xdan.frontend.model.LogoPin.BOTTOM_CENTER) }
        kotlin.test.assertEquals(cc.xdan.frontend.model.LogoPin.CENTER, app.library.value.single().effectiveLogoPin(app.settings.value.logoPin))
        app.updateSettings { it.copy(logoPin = null) }
        scene.close()
    }
}
