package cc.xdan.frontend.libretro

import cc.xdan.frontend.data.AppJson
import cc.xdan.frontend.data.AppPaths
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BOOLEAN
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

/** One core option, from any of the libretro option APIs (legacy variables, v1, v2). */
data class CoreOption(
    val key: String,
    val label: String,
    val info: String?,
    val category: String?,
    /** value → display label */
    val values: List<Pair<String, String>>,
    val default: String,
    val visible: Boolean = true,
)

/** Readers for the option structs cores hand over (layouts from libretro.h, 64-bit). */
object CoreOptionParser {
    private const val MAX_VALUES = 128
    private fun str(seg: MemorySegment, off: Long) = LibretroCore.cstr(seg.get(ADDRESS, off))

    /** RETRO_ENVIRONMENT_SET_VARIABLES: { key, "Description; a|b|c" }[] */
    fun legacy(ptr: MemorySegment): List<CoreOption> {
        val s = ptr.reinterpret(Long.MAX_VALUE)
        val out = ArrayList<CoreOption>()
        var p = 0L
        while (true) {
            val key = str(s, p) ?: break
            val desc = str(s, p + 8).orEmpty()
            val label = desc.substringBefore("; ")
            val values = desc.substringAfter("; ", "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
            out += CoreOption(key, label, null, null, values.map { it to it }, values.firstOrNull().orEmpty())
            p += 16
        }
        return out
    }

    private fun values(s: MemorySegment, off: Long): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until MAX_VALUES) {
            val v = str(s, off + i * 16L) ?: break
            out += v to (str(s, off + i * 16L + 8) ?: v)
        }
        return out
    }

    /** v1: retro_core_option_definition { key, desc, info, values[128], default } = 2080 bytes. */
    fun v1(ptr: MemorySegment): List<CoreOption> {
        val s = ptr.reinterpret(Long.MAX_VALUE)
        val out = ArrayList<CoreOption>()
        var p = 0L
        while (true) {
            val key = str(s, p) ?: break
            val vals = values(s, p + 24)
            out += CoreOption(key, str(s, p + 8) ?: key, str(s, p + 16), null, vals,
                str(s, p + 24 + MAX_VALUES * 16L) ?: vals.firstOrNull()?.first.orEmpty())
            p += 24 + MAX_VALUES * 16L + 8
        }
        return out
    }

    /**
     * v2: retro_core_options_v2 { categories*, definitions* }.
     * Definition { key, desc, desc_categorized, info, info_categorized, category_key, values[128], default } = 2104 bytes.
     */
    fun v2(ptr: MemorySegment): List<CoreOption> {
        val root = ptr.reinterpret(16)
        val cats = HashMap<String, String>()
        root.get(ADDRESS, 0).takeIf { it.address() != 0L }?.reinterpret(Long.MAX_VALUE)?.let { c ->
            var p = 0L
            while (true) { val k = str(c, p) ?: break; cats[k] = str(c, p + 8) ?: k; p += 24 }
        }
        val s = root.get(ADDRESS, 8).takeIf { it.address() != 0L }?.reinterpret(Long.MAX_VALUE) ?: return emptyList()
        val out = ArrayList<CoreOption>()
        var p = 0L
        val stride = 48 + MAX_VALUES * 16L + 8
        while (true) {
            val key = str(s, p) ?: break
            val catKey = str(s, p + 40)
            val vals = values(s, p + 48)
            out += CoreOption(
                key, (if (catKey != null) str(s, p + 16) else null) ?: str(s, p + 8) ?: key,
                str(s, p + 24), catKey?.let { cats[it] ?: it }, vals,
                str(s, p + 48 + MAX_VALUES * 16L) ?: vals.firstOrNull()?.first.orEmpty(),
            )
            p += stride
        }
        return out
    }

    /** *_INTL variants: { us*, local* } — use the English set. */
    fun intlUs(ptr: MemorySegment): MemorySegment = ptr.reinterpret(16).get(ADDRESS, 0)
}

/** Saved option values per core (core-options/<core>.json). */
class CoreOptionStore(coreName: String) {
    private val file = File(AppPaths.root, "core-options/${coreName.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.json")
    private val ser = MapSerializer(String.serializer(), String.serializer())

    fun load(): Map<String, String> = runCatching { AppJson.decodeFromString(ser, file.readText()) }.getOrDefault(emptyMap())

    fun save(values: Map<String, String>) {
        runCatching { file.parentFile.mkdirs(); file.writeText(AppJson.encodeToString(ser, values)) }
    }
}

/**
 * RETRO_ENVIRONMENT_SET_DISK_CONTROL_(EXT_)INTERFACE: the core hands over function pointers for
 * multi-disc games. The struct is copied immediately; call only on the emulation thread.
 */
class DiskControl(struct: MemorySegment, ext: Boolean) {
    private val linker = Linker.nativeLinker()
    private val ptrs = struct.reinterpret(if (ext) 80 else 56).let { s -> (0 until (if (ext) 10 else 7)).map { s.get(ADDRESS, it * 8L) } }
    private fun h(i: Int, d: FunctionDescriptor): MethodHandle? = ptrs.getOrNull(i)?.takeIf { it.address() != 0L }?.let { linker.downcallHandle(it, d) }

    private val setEject = h(0, FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_BOOLEAN))
    private val getIndex = h(2, FunctionDescriptor.of(JAVA_INT))
    private val setIndex = h(3, FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT))
    private val getCount = h(4, FunctionDescriptor.of(JAVA_INT))
    private val getLabel = if (ext) h(9, FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS, JAVA_LONG)) else null
    private val getPath = if (ext) h(8, FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS, JAVA_LONG)) else null

    fun count(): Int = (getCount?.invoke() as? Int) ?: 0
    fun index(): Int = (getIndex?.invoke() as? Int) ?: 0

    fun label(i: Int): String = Arena.ofConfined().use { a ->
        val buf = a.allocate(512)
        val viaLabel = getLabel?.let { (it.invoke(i, buf, 512L) as Boolean) && buf.getString(0).isNotBlank() } == true
        if (viaLabel) return@use buf.getString(0)
        val viaPath = getPath?.let { (it.invoke(i, buf, 512L) as Boolean) && buf.getString(0).isNotBlank() } == true
        if (viaPath) File(buf.getString(0)).nameWithoutExtension else "Disc ${i + 1}"
    }

    /** Open tray → select image → close tray. */
    fun swap(i: Int): Boolean {
        if (setEject?.invoke(true) as? Boolean == false) return false
        val ok = setIndex?.invoke(i) as? Boolean ?: false
        setEject?.invoke(false)
        return ok
    }
}

data class DiscState(val count: Int, val index: Int, val labels: List<String>)
