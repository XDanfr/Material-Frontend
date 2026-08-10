package app.materialfrontend.ui.brand

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Renders the app-icon SVG (512×512 design) to PNG/ICO with Skia — used for window/dock icons and packaging. */
object IconRaster {
    fun appIconSvg(): ByteArray = IconRaster::class.java.getResourceAsStream("/icons/app-icon.svg")!!.use { it.readBytes() }

    fun png(svg: ByteArray, size: Int): ByteArray {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        dom.setContainerSize(512f, 512f)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.scale(size / 512f, size / 512f)
        dom.render(surface.canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    /** Multi-resolution .ico with PNG-compressed entries (Windows Vista+). */
    fun ico(svg: ByteArray, sizes: List<Int> = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)): ByteArray {
        val images = sizes.map { it to png(svg, it) }
        val header = ByteBuffer.allocate(6 + 16 * images.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(images.size.toShort())
        var offset = 6 + 16 * images.size
        images.forEach { (s, data) ->
            header.put((if (s >= 256) 0 else s).toByte()).put((if (s >= 256) 0 else s).toByte()).put(0).put(0)
            header.putShort(1).putShort(32).putInt(data.size).putInt(offset)
            offset += data.size
        }
        return ByteArrayOutputStream().apply { write(header.array()); images.forEach { write(it.second) } }.toByteArray()
    }
}
