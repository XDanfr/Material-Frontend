package cc.xdan.frontend.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import cc.xdan.frontend.ui.theme.LocalArtworkSeed
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.util.Collections

/** Decodes local artwork off the UI thread, downscaled, with an LRU cache. */
object ImageCache {
    private val cache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 400
    })
    private val seeds: MutableMap<String, Color?> = Collections.synchronizedMap(HashMap())

    suspend fun load(path: String, maxDim: Int): ImageBitmap? {
        val key = "$path@$maxDim"
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                if (!f.isFile) return@runCatching null
                val img = Image.makeFromEncoded(f.readBytes())
                val scale = minOf(1f, maxDim.toFloat() / maxOf(img.width, img.height))
                val out = if (scale >= 1f) img else {
                    val w = (img.width * scale).toInt().coerceAtLeast(1); val h = (img.height * scale).toInt().coerceAtLeast(1)
                    val surface = Surface.makeRasterN32Premul(w, h)
                    surface.canvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
                        FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), Paint(), true)
                    surface.makeImageSnapshot()
                }
                out.toComposeImageBitmap().also { cache[key] = it }
            }.getOrNull()
        }
    }

    suspend fun seed(path: String): Color? {
        if (seeds.containsKey(path)) return seeds[path]
        val bmp = load(path, 128) ?: return null
        return withContext(Dispatchers.Default) { runCatching { bmp.themeColorOrNull() }.getOrNull() }.also { seeds[path] = it }
    }
}

@Composable
fun rememberImage(path: String?, maxDim: Int = 512): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, path, maxDim) { value = path?.let { ImageCache.load(it, maxDim) } }

/** Publishes the artwork's theme colour so the whole UI re-tints to the focused game. */
@Composable
fun ArtworkSeedEffect(path: String?) {
    val seed = LocalArtworkSeed.current
    LaunchedEffect(path) { seed.value = path?.let { ImageCache.seed(it) } }
}
