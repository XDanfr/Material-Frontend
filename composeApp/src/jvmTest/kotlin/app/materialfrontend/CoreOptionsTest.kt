package app.materialfrontend

import app.materialfrontend.libretro.CoreOptionParser
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import kotlin.test.Test
import kotlin.test.assertEquals

/** Builds the libretro option structs in native memory exactly as a core would, then parses them. */
class CoreOptionsTest {
    private fun Arena.str(s: String?): MemorySegment = s?.let { allocateFrom(it) } ?: MemorySegment.NULL

    @Test fun parsesLegacyVariables() = Arena.ofConfined().use { a ->
        val arr = a.allocate(16L * 3, 8)
        arr.set(ADDRESS, 0, a.str("snes9x_region")); arr.set(ADDRESS, 8, a.str("Console Region; auto|ntsc|pal"))
        arr.set(ADDRESS, 16, a.str("snes9x_overclock")); arr.set(ADDRESS, 24, a.str("SuperFX Overclock; 100%|150%|200%"))
        val opts = CoreOptionParser.legacy(arr)
        assertEquals(2, opts.size)
        assertEquals("Console Region", opts[0].label)
        assertEquals("auto", opts[0].default)
        assertEquals(listOf("100%", "150%", "200%"), opts[1].values.map { it.first })
    }

    @Test fun parsesV2WithCategories() = Arena.ofConfined().use { a ->
        val defSize = 48L + 128 * 16 + 8
        val defs = a.allocate(defSize * 2, 8)   // second entry zeroed = terminator
        defs.set(ADDRESS, 0, a.str("mgba_skip_bios"))
        defs.set(ADDRESS, 8, a.str("System > Skip BIOS Intro"))
        defs.set(ADDRESS, 16, a.str("Skip BIOS Intro"))
        defs.set(ADDRESS, 24, a.str("Skips the boot logo."))
        defs.set(ADDRESS, 40, a.str("system"))
        defs.set(ADDRESS, 48, a.str("OFF")); defs.set(ADDRESS, 56, a.str("Disabled"))
        defs.set(ADDRESS, 64, a.str("ON")); defs.set(ADDRESS, 72, a.str("Enabled"))
        defs.set(ADDRESS, 48 + 128 * 16L, a.str("OFF"))
        val cats = a.allocate(24L * 2, 8)
        cats.set(ADDRESS, 0, a.str("system")); cats.set(ADDRESS, 8, a.str("System"))
        val root = a.allocate(16, 8)
        root.set(ADDRESS, 0, cats); root.set(ADDRESS, 8, defs)

        val o = CoreOptionParser.v2(root).single()
        assertEquals("mgba_skip_bios", o.key)
        assertEquals("Skip BIOS Intro", o.label)          // categorised label when a category is set
        assertEquals("System", o.category)
        assertEquals(listOf("OFF" to "Disabled", "ON" to "Enabled"), o.values)
        assertEquals("OFF", o.default)
    }
}
