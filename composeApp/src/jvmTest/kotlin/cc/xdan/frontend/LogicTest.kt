package cc.xdan.frontend

import cc.xdan.frontend.data.Ports
import cc.xdan.frontend.data.RomScanner
import cc.xdan.frontend.data.TitleCleaner
import cc.xdan.frontend.data.Vdf
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.model.RomFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicTest {
    @Test fun cleansNoIntroTitles() {
        assertEquals("The Legend of Zelda - A Link to the Past", TitleCleaner.clean("Legend of Zelda, The - A Link to the Past (USA) [!]"))
        assertEquals("Super Mario World", TitleCleaner.clean("Super Mario World (USA)"))
        assertEquals("USA", TitleCleaner.region("Super Mario World (USA)"))
    }

    @Test fun parsesVdf() {
        val v = Vdf.parse("""
            "libraryfolders" { "0" { "path" "C:\\Steam" "apps" { "10" "123" } } "1" { "path" "/games" } }
        """)
        @Suppress("UNCHECKED_CAST") val lf = v["libraryfolders"] as Map<String, Map<String, Any>>
        assertEquals("C:\\Steam", lf["0"]!!["path"])
        assertEquals("/games", lf["1"]!!["path"])
    }

    @Test fun guessesPorts() {
        assertEquals("n64", Ports.guess(File("/x/Zelda64Recompiled.exe")).platformId)
        assertEquals("xbox360", Ports.guess(File("/x/UnleashedRecomp")).platformId)
        assertEquals("The Legend of Zelda: Ocarina of Time", Ports.guess(File("/x/soh.exe")).title)
        assertEquals("My Cool Game", Ports.guess(File("/x/MyCoolGame-v1.2.3-x86_64.AppImage")).title)
    }

    @Test fun splitsArgsWithQuotes() {
        assertEquals(listOf("-f", "-L", "{core}", "{rom}"), Launchers.splitArgs("-f -L \"{core}\" \"{rom}\""))
        assertEquals(listOf("--x", "a b"), Launchers.splitArgs("--x \"a b\""))
    }

    @Test fun scansFoldersByAliasAndExtension() {
        val root = Files.createTempDirectory("mf-scan").toFile()
        File(root, "snes").mkdirs(); File(root, "snes/Super Game (USA).sfc").writeText("x")
        File(root, "psx").mkdirs(); File(root, "psx/Disc Game (USA).cue").writeText("FILE \"Disc Game (USA).bin\" BINARY")
        File(root, "psx/Disc Game (USA).bin").writeText("x")
        File(root, "mixed").mkdirs(); File(root, "mixed/Pocket.gba").writeText("x"); File(root, "mixed/ambiguous.iso").writeText("x")
        File(root, "mixed/Next Gen.nup").writeText("x")
        File(root, "switch").mkdirs(); File(root, "switch/Old Gen.nsp").writeText("x")
        val r = RomScanner().scan(listOf(RomFolder(root.path)), emptyMap())
        val byPlatform = r.games.groupBy { it.platformId }
        assertEquals(listOf("Super Game"), byPlatform["snes"]?.map { it.title })
        assertEquals(1, byPlatform["psx"]?.size, "cue should hide its bin")
        assertEquals(listOf("Pocket"), byPlatform["gba"]?.map { it.title })
        assertEquals(listOf("Next Gen"), byPlatform["switch2"]?.map { it.title }, ".nup is Switch 2 anywhere")
        assertEquals(listOf("Old Gen"), byPlatform["switch"]?.map { it.title })
        assertTrue(r.games.none { it.path.endsWith(".iso") }, "ambiguous ext outside a system folder is skipped")
        root.deleteRecursively()
    }
}
