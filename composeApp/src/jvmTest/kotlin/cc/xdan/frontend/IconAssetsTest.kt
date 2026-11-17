package cc.xdan.frontend

import cc.xdan.frontend.ui.brand.IconRaster
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Always checks the icon renders; with MF_WRITE_ICONS=<repo root> also regenerates packaging icons. */
class IconAssetsTest {
    @Test
    fun rendersAppIcon() {
        val svg = IconRaster.appIconSvg()
        val png = IconRaster.png(svg, 64)
        assertTrue(png.size > 500)
        val root = System.getenv("MF_WRITE_ICONS")?.let(::File) ?: return
        File(root, "packaging/linux/icon.png").writeBytes(IconRaster.png(svg, 512))
        File(root, "packaging/windows/icon.ico").writeBytes(IconRaster.ico(svg))
    }
}
