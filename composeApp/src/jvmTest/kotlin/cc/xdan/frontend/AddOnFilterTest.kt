package cc.xdan.frontend

import cc.xdan.frontend.data.AddOns
import cc.xdan.frontend.data.RomScanner
import cc.xdan.frontend.model.RomFolder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddOnFilterTest {
    @Test fun recognisesSwitchAddOns() {
        assertFalse(AddOns.isAddOn("Super Mario Odyssey [0100000000010000][v0].nsp"))
        assertTrue(AddOns.isAddOn("Super Mario Odyssey [0100000000010800][v917504].nsp"))
        assertTrue(AddOns.isAddOn("Mario Kart 8 Deluxe [0100152000023001][v0].nsp"))
        assertTrue(AddOns.isAddOn("Zelda TOTK (Update) v1.2.1.nsp"))
        assertTrue(AddOns.isAddOn("Game [DLC] Pack.nsz"))
        assertFalse(AddOns.isAddOn("Patchwork Heroes (USA).iso"))
        assertFalse(AddOns.isAddOn("Super Mario Bros. (World).nes"))
    }

    @Test fun scannerSkipsAddOnsAndAddOnFolders() {
        val root = createTempDirectory("mf-addons").toFile()
        val sw = File(root, "switch").apply { mkdirs() }
        File(sw, "Odyssey [0100000000010000][v0].nsp").writeText("x")
        File(sw, "Odyssey [0100000000010800][v917504].nsp").writeText("x")
        File(sw, "MK8 [0100152000023001][v0].nsp").writeText("x")
        File(sw, "updates").mkdirs(); File(sw, "updates/Anything.nsp").writeText("x")
        File(sw, "DLC").mkdirs(); File(sw, "DLC/Other.nsp").writeText("x")
        val games = RomScanner().scan(listOf(RomFolder(root.absolutePath)), emptyMap()).games
        assertEquals(listOf("Odyssey"), games.map { it.title })
        root.deleteRecursively()
    }
}
