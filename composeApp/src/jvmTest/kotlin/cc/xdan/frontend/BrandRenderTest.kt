package cc.xdan.frontend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.xdan.frontend.ui.brand.BrandIcons
import cc.xdan.frontend.ui.brand.IconTransition
import cc.xdan.frontend.ui.brand.drawTransition
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/** Opt-in (MF_SCREENSHOTS=<dir>): renders the icon set (row 1) and the morph cycle at 0.25/0.5/0.75 (rows 2–4). */
class BrandRenderTest {
    @Test
    fun renderIcons() {
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File) ?: return
        out.mkdirs()
        val icons = BrandIcons.cycle
        val cell = 160
        val scene = ImageComposeScene(cell * icons.size, cell * 4, Density(1f)) {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val cs = MaterialTheme.colorScheme
                Column(Modifier.background(Color.White)) {
                    for (row in 0 until 4) Row {
                        icons.forEachIndexed { i, icon ->
                            val t = if (row == 0) IconTransition(icon, icon) else IconTransition(icon, icons[(i + 1) % icons.size])
                            val p = if (row == 0) 1f else row * 0.25f
                            Canvas(Modifier.size(cell.dp)) { drawTransition(t, p, cs, spinKeys = emptySet()) }
                        }
                    }
                }
            }
        }
        File(out, "brand-icons.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
