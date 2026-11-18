package cc.xdan.frontend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.ui.brand.PlatformMarks
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every platform gets a mark (logo or outline); renders a contact sheet. */
class PlatformMarksTest {
    @Test fun allMarksBuildAndRender() {
        PlatformMarks.allNames.forEach { assertNotNull(PlatformMarks.named(it), it) }
        val consoles = Platforms.all.map { it.id }.filter { it !in setOf("pc", "dos", "msx", "amazon", "xbox-pc") && !it.startsWith("custom") }
        val missing = consoles.filter { PlatformMarks.forPlatform(it) == null }
        assertTrue(missing.isEmpty(), "no mark for $missing")
        val out = System.getenv("MF_SCREENSHOTS")?.let(::File)?.also { it.mkdirs() } ?: return
        val ids = Platforms.all.map { it.id }.filter { PlatformMarks.forPlatform(it) != null }
        val scene = ImageComposeScene(1200, 120 * ((ids.size + 9) / 10), Density(1f)) {
            Column(Modifier.background(Color.White).padding(8.dp)) {
                ids.chunked(10).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { id ->
                            Column(Modifier.size(112.dp, 112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(PlatformMarks.forPlatform(id)!!, id, Modifier.size(56.dp), tint = Color(0xFF3B3355))
                                Text(id, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        File(out, "platform-marks.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
