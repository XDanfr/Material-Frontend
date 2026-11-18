package cc.xdan.frontend

import cc.xdan.frontend.data.RomScanner
import cc.xdan.frontend.emulators.EmulatorDetector
import cc.xdan.frontend.model.CustomSystem
import cc.xdan.frontend.model.Platforms
import cc.xdan.frontend.model.RomFolder
import cc.xdan.frontend.ui.settings.AddEmulatorLogic
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanRulesTest {
    private fun scan(root: File, allowed: Set<String>?) =
        RomScanner().scan(listOf(RomFolder(root.path)), emptyMap(), allowed).games.map { File(it.path).name to it.platformId }.toSet()

    @Test fun documentsFolderOnlyYieldsRunnableGames() {
        val docs = Files.createTempDirectory("docs").toFile()
        listOf("archive.tar.gz", "backup.tar", "gradlew.bat", "setup.exe", "notes.zip", "disk.iso", "Pocket.gba").forEach { File(docs, it).writeText("x") }
        assertEquals(setOf("Pocket.gba" to "gba"), scan(docs, setOf("gba", "ps2", "dos")), "generic files never become games outside a system folder")
        assertEquals(emptySet(), scan(docs, emptySet()), "nothing runnable → nothing scanned")
        docs.deleteRecursively()
    }

    @Test fun systemFoldersStillWorkWhenRunnable() {
        val root = Files.createTempDirectory("roms").toFile()
        File(root, "ps2").mkdirs(); File(root, "ps2/Game.iso").writeText("x")
        assertEquals(setOf("Game.iso" to "ps2"), scan(root, setOf("ps2")))
        assertEquals(emptySet(), scan(root, setOf("gba")), "no PS2 emulator → no PS2 games")
        root.deleteRecursively()
    }

    @Test fun customSystemsAreScannedByTheirExtensions() {
        val root = Files.createTempDirectory("custom").toFile()
        File(root, "cart.p8").writeText("x"); File(root, "readme.txt").writeText("x")
        try {
            Platforms.setCustom(listOf(CustomSystem("custom-pico-8", "PICO-8", listOf("p8"))))
            assertEquals(setOf("cart.p8" to "custom-pico-8"), scan(root, setOf("custom-pico-8")))
        } finally { Platforms.setCustom(emptyList()); root.deleteRecursively() }
    }

    @Test fun addEmulatorAnswersBecomeAnEmulator() {
        assertEquals(listOf("p8", "png"), AddEmulatorLogic.parseExtensions(".P8, *.png; p8"))
        val (cfg, custom) = AddEmulatorLogic.build("/apps/pico8", "PICO-8", "", null, emptyList(), "PICO-8", listOf("p8"))
        assertEquals("custom-pico-8", custom?.id)
        assertEquals(listOf("custom-pico-8"), cfg.platforms)
        val (gba, none) = AddEmulatorLogic.build("/apps/vba", "VBA", "\"{rom}\"", null, listOf("gba", "gbc"), null, emptyList())
        assertEquals(listOf("gba", "gbc"), gba.platforms); assertNull(none)
    }

    @Test fun recognisesEmulatorsByName() {
        val d = EmulatorDetector()
        assertEquals("dolphin", d.recognize(File("/Applications/Dolphin.app/Contents/MacOS/Dolphin"))?.id)
        assertEquals("duckstation", d.recognize(File("C:/Emu/duckstation-qt-x64-ReleaseLTCG.exe"))?.id)
        assertEquals("xenia_canary", d.recognize(File("/home/me/Applications/xenia_canary-x86_64.AppImage"))?.id)
        assertNull(d.recognize(File("/opt/mystery-emu")))
        assertTrue(Platforms.uniqueExtensions["gz"] == null && Platforms.uniqueExtensions["bat"] == null)
    }
}
