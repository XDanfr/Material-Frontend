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
        assertFalse(AddOns.isAddOn("Super Mario Odyssey [0100000000010000][v0].nsp", "switch"))
        assertTrue(AddOns.isAddOn("Super Mario Odyssey [0100000000010800][v917504].nsp", "switch"))
        assertTrue(AddOns.isAddOn("Mario Kart 8 Deluxe [0100152000023001][v0].nsp", "switch"))
        assertTrue(AddOns.isAddOn("Zelda TOTK (Update) v1.2.1.nsp", "switch"))
        assertTrue(AddOns.isAddOn("Game [DLC] Pack.nsz", "switch2"))
        assertFalse(AddOns.isAddOn("Patchwork Heroes (USA).iso", "psp"))
        assertFalse(AddOns.isAddOn("Super Mario Bros. (World).nes", "nes"))
    }

    @Test fun keepsMergedDumps() {
        assertFalse(AddOns.isAddOn("Splatoon Raiders [0100ABCD12340000][v131072].nup", "switch2"))
        assertFalse(AddOns.isAddOn("Splatoon Raiders [0100ABCD12340800].nup", "switch2"))
        assertFalse(AddOns.isAddOn("Zelda TOTK [Base + Update + DLC].xci", "switch"))
        assertFalse(AddOns.isAddOn("Mario Wonder (Game+Upd).nsz", "switch"))
        assertTrue(AddOns.isAddOn("Mario Wonder (Update).nsz", "switch"))
    }

    @Test fun switchRulesStayOnSwitch() {
        assertFalse(AddOns.isAddOn("Some Game [0100000000010800].iso", "ps2"))
        assertFalse(AddOns.isAddOn("Some Game [v131072].iso", "gc"))
    }

    @Test fun recognisesPs4AndXbox360Updates() {
        assertFalse(AddOns.isAddOn("UP0001-CUSA12345_00-GAME000000000000-A0100-V0100.pkg", "ps4"))
        assertTrue(AddOns.isAddOn("UP0001-CUSA12345_00-GAME000000000000-A0105-V0100.pkg", "ps4"))
        assertTrue(AddOns.isAddOn("Bloodborne_patch.pkg", "ps4"))
        assertTrue(AddOns.isAddOn("TU_10BD0K1_0000014000000.00000000000O1", "xbox360"))
        assertTrue(AddOns.isAddOn("Blur TU2.xex", "xbox360"))
        assertFalse(AddOns.isAddOn("Turok.iso", "xbox360"))
        assertFalse(AddOns.isAddOn("UP0001-CUSA12345_00-GAME000000000000-A0105-V0100.pkg", "ps3"))
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
