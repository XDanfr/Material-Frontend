package app.materialfrontend.ui.brand

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.rectangle
import kotlin.math.cos
import kotlin.math.sin

/** Colour roles used by the icon set (baseline M3 roles from the SVGs, mapped onto the live scheme). */
enum class IconRole {
    PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER, SECONDARY, TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER,
    /** #DCCBFB – soft backdrop between primary container and primary. */
    SOFT,
    /** #7A63B8 – mid primary backdrop. */
    MID,
    /** #8D79C7 – cartridge ridges and pins. */
    RIDGE,
    /** #2B2930 – the dark "play stack" backdrop; flips with the theme. */
    DARK_SURFACE,
    /** Fixed-tone roles from the dark play-stack icon. */
    FIXED_TERTIARY, FIXED_PRIMARY, ON_FIXED_PRIMARY,
    /** Colour blocks behind the cartridge's cut-outs (#8D79C7 light / #4F378B dark). */
    BLOCKS,
    /** Not painted: cut out of the backdrop ("bg") layer, so whatever is behind shows through. */
    HOLE;

    fun color(cs: ColorScheme): Color = when (this) {
        PRIMARY -> cs.primary
        PRIMARY_CONTAINER -> cs.primaryContainer
        ON_PRIMARY_CONTAINER -> cs.onPrimaryContainer
        SECONDARY -> cs.secondary
        TERTIARY -> cs.tertiary
        TERTIARY_CONTAINER -> cs.tertiaryContainer
        ON_TERTIARY_CONTAINER -> cs.onTertiaryContainer
        SOFT -> lerp(cs.primaryContainer, cs.primary, 0.18f)
        MID -> lerp(cs.primary, cs.primaryContainer, 0.25f)
        RIDGE -> lerp(cs.primary, cs.primaryContainer, 0.4f)
        DARK_SURFACE -> if (cs.surface.luminance() < 0.5f) cs.surfaceContainerHighest else cs.inverseSurface
        FIXED_TERTIARY -> cs.tertiaryFixedDim
        FIXED_PRIMARY -> cs.primaryFixedDim
        ON_FIXED_PRIMARY -> cs.onPrimaryFixed
        BLOCKS -> if (cs.surface.luminance() < 0.5f) cs.primaryContainer else lerp(cs.primary, cs.primaryContainer, 0.4f)
        HOLE -> Color.Transparent
    }
}

/**
 * One layer of an icon, in the icons' 512×512 design space. Layers with the same [key] in two
 * icons morph into each other; unmatched layers grow from / shrink to their own centre.
 */
class IconLayer(val key: String, val polygon: RoundedPolygon, val role: IconRole)

class BrandIcon(val name: String, val layers: List<IconLayer>) {
    fun withLayer(key: String, polygon: RoundedPolygon, role: IconRole) =
        BrandIcon(name, layers.map { if (it.key == key) IconLayer(key, polygon, role) else it })
}

/** Global paint order so morphing layers never jump in z. */
private val zOrder = listOf("blockTop", "blockBottom", "bg", "bg2", "ridge0", "ridge1", "ridge2", "gripL", "gripR", "body", "barV", "barH",
    "pin0", "pin1", "pin2", "pin3", "pin4", "core", "a", "b", "c", "d")

/**
 * The seven icons from the brand set, rebuilt from the same M3 Expressive library shapes and
 * placements as the SVGs (packaging/icons), so they can morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object BrandIcons {
    /** Places a normalised library shape so its design box spans [size] px, centred, rotated. */
    fun place(shape: RoundedPolygon, cx: Float, cy: Float, size: Float, rotation: Float = 0f): RoundedPolygon {
        val r = Math.toRadians(rotation.toDouble())
        val c = cos(r).toFloat(); val s = sin(r).toFloat()
        return shape.normalized().transformed { x, y ->
            val dx = (x - 0.5f) * size; val dy = (y - 0.5f) * size
            TransformResult(cx + dx * c - dy * s, cy + dx * s + dy * c)
        }
    }

    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float): RoundedPolygon =
        RoundedPolygon.rectangle(width = w, height = h, rounding = CornerRounding(r), centerX = x + w / 2, centerY = y + h / 2)

    /** The cartridge outline: 20/44/44 rounded corners and a chamfered bottom-right. */
    private val cartridgeBody = RoundedPolygon(
        vertices = floatArrayOf(132f, 92f, 380f, 92f, 380f, 340f, 340f, 420f, 132f, 420f),
        perVertexRounding = listOf(CornerRounding(20f), CornerRounding(44f), CornerRounding.Unrounded, CornerRounding.Unrounded, CornerRounding(44f)),
    )

    private fun l(key: String, p: RoundedPolygon, role: IconRole) = IconLayer(key, p, role)

    /**
     * 04 — the app icon (cut-out layered version): label window, ridges and pins are holes through
     * the body; two colour blocks sit underneath and show through the ridge/pin holes.
     */
    val cartridge = BrandIcon("cartridge", buildList {
        add(l("blockTop", rrect(166f, 104f, 180f, 28f, 12f), IconRole.BLOCKS))
        add(l("blockBottom", rrect(166f, 362f, 146f, 50f, 14f), IconRole.BLOCKS))
        add(l("bg", cartridgeBody, IconRole.PRIMARY))
        listOf(176f, 240f, 304f).forEachIndexed { i, x -> add(l("ridge$i", rrect(x, 112f, 32f, 12f, 6f), IconRole.HOLE)) }
        add(l("body", rrect(160f, 148f, 192f, 176f, 28f), IconRole.HOLE))
        add(l("core", place(MaterialShapes.Flower, 256f, 236f, 132f), IconRole.TERTIARY))
        (0 until 5).forEach { i -> add(l("pin$i", rrect(176f + i * 28f, 372f, 14f, 30f, 7f), IconRole.HOLE)) }
    })

    /** 01 — face buttons on a soft burst. Top/right buttons feed the d-pad arms of the next icon. */
    val faceButtons = BrandIcon("face-buttons", listOf(
        l("bg", place(MaterialShapes.SoftBurst, 256f, 256f, 400f, 8f), IconRole.SOFT),
        l("barV", place(MaterialShapes.Sunny, 256f, 146f, 128f, 10f), IconRole.TERTIARY),
        l("barH", place(MaterialShapes.Cookie4Sided, 366f, 256f, 124f, 45f), IconRole.PRIMARY),
        l("core", place(MaterialShapes.Flower, 256f, 366f, 128f), IconRole.ON_PRIMARY_CONTAINER),
        l("d", place(MaterialShapes.Gem, 146f, 256f, 124f, -90f), IconRole.SECONDARY),
    ))

    /** 02 — d-pad with a cookie hub over a very-sunny burst. */
    val dpad = BrandIcon("dpad", listOf(
        l("bg", place(MaterialShapes.VerySunny, 256f, 256f, 440f), IconRole.MID),
        l("barV", rrect(196f, 96f, 120f, 320f, 36f), IconRole.PRIMARY_CONTAINER),
        l("barH", rrect(96f, 196f, 320f, 120f, 36f), IconRole.PRIMARY_CONTAINER),
        l("core", place(MaterialShapes.Cookie4Sided, 256f, 256f, 96f, 45f), IconRole.TERTIARY),
    ))

    /** 03 — controller in a 12-sided cookie. */
    val controller = BrandIcon("controller", listOf(
        l("bg", place(MaterialShapes.Cookie12Sided, 256f, 256f, 420f), IconRole.TERTIARY),
        l("body", rrect(118f, 190f, 276f, 132f, 66f), IconRole.TERTIARY_CONTAINER),
        l("gripL", place(MaterialShapes.Circle, 162f, 316f, 104f), IconRole.TERTIARY_CONTAINER),
        l("gripR", place(MaterialShapes.Circle, 350f, 316f, 104f), IconRole.TERTIARY_CONTAINER),
        l("barV", rrect(152f, 230f, 26f, 68f, 8f), IconRole.TERTIARY),
        l("barH", rrect(131f, 251f, 68f, 26f, 8f), IconRole.TERTIARY),
        l("core", place(MaterialShapes.Cookie4Sided, 342f, 238f, 36f, 45f), IconRole.PRIMARY),
        l("b", place(MaterialShapes.Circle, 374f, 270f, 30f), IconRole.ON_TERTIARY_CONTAINER),
        l("c", place(MaterialShapes.Cookie4Sided, 342f, 300f, 36f, 45f), IconRole.PRIMARY),
        l("d", place(MaterialShapes.Circle, 310f, 270f, 30f), IconRole.ON_TERTIARY_CONTAINER),
    ))

    /** 05 — layered 9-sided cookies with a play triangle. */
    val playStack = BrandIcon("play-stack", listOf(
        l("bg", place(MaterialShapes.VerySunny, 256f, 256f, 420f, 14f), IconRole.DARK_SURFACE),
        l("bg2", place(MaterialShapes.Cookie9Sided, 270f, 272f, 300f), IconRole.FIXED_TERTIARY),
        l("body", place(MaterialShapes.Cookie9Sided, 250f, 248f, 300f), IconRole.FIXED_PRIMARY),
        l("core", place(MaterialShapes.Triangle, 266f, 248f, 150f, 90f), IconRole.ON_FIXED_PRIMARY),
    ))

    /** 06 — pixel circle + pixel play. */
    val pixelPlay = BrandIcon("pixel-play", listOf(
        l("bg", place(MaterialShapes.PixelCircle, 256f, 256f, 360f), IconRole.PRIMARY),
        l("core", place(MaterialShapes.PixelTriangle, 270f, 256f, 150f), IconRole.PRIMARY_CONTAINER),
    ))

    /** 07 — square → cookie → triangle in a cartridge-slot pill. The square becomes the cartridge label next. */
    val morphSlot = BrandIcon("morph-slot", listOf(
        l("bg", rrect(48f, 180f, 416f, 152f, 76f), IconRole.PRIMARY_CONTAINER),
        l("body", place(MaterialShapes.Square, 132f, 256f, 92f), IconRole.SECONDARY),
        l("core", place(MaterialShapes.Cookie6Sided, 256f, 256f, 104f), IconRole.PRIMARY),
        l("b", place(MaterialShapes.Triangle, 386f, 256f, 104f, 90f), IconRole.TERTIARY),
    ))

    /** Loading cycle: each step was chosen so the obvious layers flow into each other. */
    val cycle = listOf(cartridge, faceButtons, dpad, controller, playStack, pixelPlay, morphSlot)

    /** Quick loader: the cartridge with its label shape running the M3 loading-indicator sequence. */
    val cartridgeLoading: List<BrandIcon> = listOf(
        MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie9Sided, MaterialShapes.Pentagon,
        MaterialShapes.Pill, MaterialShapes.Sunny, MaterialShapes.Cookie4Sided, MaterialShapes.Oval,
    ).map { shape -> cartridge.withLayer("core", place(shape, 256f, 236f, 132f), IconRole.TERTIARY) }

    fun byName(name: String) = cycle.firstOrNull { it.name == name } ?: cartridge
}

/** A precomputed transition between two icons. */
class IconTransition(from: BrandIcon, to: BrandIcon) {
    class Track(val key: String, val morph: Morph, val fromRole: IconRole?, val toRole: IconRole?,
                val fromCenter: Pair<Float, Float>, val toCenter: Pair<Float, Float>)

    val tracks: List<Track>

    init {
        val a = from.layers.associateBy { it.key }
        val b = to.layers.associateBy { it.key }
        val keys = (a.keys + b.keys).sortedBy { k -> zOrder.indexOf(k).let { if (it < 0) 99 else it } }
        tracks = keys.map { k ->
            val la = a[k]; val lb = b[k]
            val pa = la?.polygon ?: collapse(lb!!.polygon)
            val pb = lb?.polygon ?: collapse(la!!.polygon)
            Track(k, Morph(pa, pb), la?.role, lb?.role, pa.centerX to pa.centerY, pb.centerX to pb.centerY)
        }
    }

    /** The same shape shrunk to 4% around its centre (morph start/end for appearing layers). */
    private fun collapse(p: RoundedPolygon): RoundedPolygon {
        val cx = p.centerX; val cy = p.centerY
        return p.transformed { x, y -> TransformResult(cx + (x - cx) * 0.04f, cy + (y - cy) * 0.04f) }
    }
}
