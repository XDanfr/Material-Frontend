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
        scene.close()
    }
}
