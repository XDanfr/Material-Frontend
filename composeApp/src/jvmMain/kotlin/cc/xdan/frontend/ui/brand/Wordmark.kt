package cc.xdan.frontend.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

/** The supplied brand artwork (packaging/brand). Geometry is used as-is; colours follow the live theme. */
enum class Wordmark(val file: String, val aspect: Float) {
    /** Cartridge + "Material Frontend" on one line. */
    HORIZONTAL("wordmark-horizontal.svg", 1310f / 240f),
    /** Cartridge above two lines of text. */
    STACKED("wordmark-stacked.svg", 760f / 580f),
    /** "Material Frontend" text only. */
    TEXT("wordmark-text.svg", 1090f / 170f),
    /** Static cartridge (cut-out version). */
    ICON("icon-cartridge.svg", 248f / 328f),
}

private object WordmarkCache {
    val source = HashMap<Wordmark, String>()
    val painters = HashMap<Pair<Wordmark, List<Int>>, Painter>()
}

/**
 * The light-variant files use baseline M3 tones; the dark variants are the same geometry with the
 * dark-scheme tones of the same roles, so swapping in the current scheme's roles covers both
 * (and artwork-tinted themes).
 */
private fun roleColors(cs: ColorScheme): Map<String, Color> {
    val dark = cs.surface.luminance() < 0.5f
    return mapOf(
        "#6750A4" to cs.primary,                 // "Material", cartridge body
        "#7D5260" to cs.tertiary,                // flower
        "#8D79C7" to if (dark) cs.primaryContainer else lerp(cs.primary, cs.primaryContainer, 0.4f), // blocks
        "#1D1B20" to cs.onSurface,               // "Frontend"
    )
}

@Composable
fun BrandArtwork(kind: Wordmark, modifier: Modifier = Modifier, contentDescription: String? = "Material Frontend") {
    val colors = roleColors(MaterialTheme.colorScheme)
    val key = kind to colors.values.map { it.toArgb() }
    val painter = remember(key) {
        WordmarkCache.painters.getOrPut(key) {
            val src = WordmarkCache.source.getOrPut(kind) {
                Wordmark::class.java.getResourceAsStream("/brand/${kind.file}")!!.use { it.readBytes().decodeToString() }
            }
            val recoloured = colors.entries.fold(src) { acc, (hex, c) ->
                acc.replace("fill=\"$hex\"", "fill=\"#%06X\"".format(c.toArgb() and 0xFFFFFF), ignoreCase = true)
            }
            loadSvgPainter(recoloured.byteInputStream(), Density(1f))
        }
    }
    Image(painter, contentDescription, modifier.aspectRatio(kind.aspect), contentScale = ContentScale.Fit)
}
