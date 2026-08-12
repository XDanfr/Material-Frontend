package app.materialfrontend

import app.materialfrontend.data.Gamelists
import app.materialfrontend.model.Game
import app.materialfrontend.model.RomFolder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportersTest {
    private fun tmp() = Files.createTempDirectory("mf-import").toFile()

    @Test fun readsEsGamelist() {
        val root = tmp(); val snes = File(root, "snes").apply { mkdirs() }
        File(snes, "Super Game (USA).sfc").writeText("x")
        File(snes, "media").mkdirs(); File(snes, "media/sg.png").writeBytes(ByteArray(10))
        File(snes, "gamelist.xml").writeText("""
            <?xml version="1.0"?>
            <gameList><game><path>./Super Game (USA).sfc</path><name>Super Game!</name><desc>Jump.</desc>
              <releasedate>19910821T000000</releasedate><developer>Dev</developer><genre>Platform, Action</genre>
              <rating>0.8</rating><favorite>true</favorite><playcount>3</playcount><image>./media/sg.png</image></game></gameList>
        """.trimIndent())
        val game = Game("1", "Super Game", "snes", path = File(snes, "Super Game (USA).sfc").absolutePath)
        val out = Gamelists.apply(listOf(game), listOf(RomFolder(root.path))).single()
        assertEquals("Super Game!", out.title)
        assertEquals("Jump.", out.meta.description)
        assertEquals(1991, out.meta.releaseYear)
        assertEquals(listOf("Platform", "Action"), out.meta.genres)
        assertTrue(out.favorite); assertEquals(3, out.playCount)
        assertTrue(out.assets.cover!!.endsWith("sg.png"))
        root.deleteRecursively()
    }

    @Test fun readsPegasusMetadata() {
        val root = tmp(); val gba = File(root, "gba").apply { mkdirs() }
        File(gba, "pocket.gba").writeText("x")
        File(gba, "media/pocket").mkdirs(); File(gba, "media/pocket/boxFront.png").writeBytes(ByteArray(10))
        File(gba, "metadata.pegasus.txt").writeText("""
            collection: Game Boy Advance
            extension: gba

            game: Pocket Quest
            file: pocket.gba
            developer: Studio
            release: 2003-05-01
            rating: 90%
            description: A long
              multi-line description.
        """.trimIndent())
        val g = Game("2", "pocket", "gba", path = File(gba, "pocket.gba").absolutePath)
        val out = Gamelists.apply(listOf(g), listOf(RomFolder(root.path))).single()
        assertEquals("Pocket Quest", out.title)
        assertEquals("A long multi-line description.", out.meta.description)
        assertEquals(2003, out.meta.releaseYear)
        assertEquals(0.9f, out.meta.rating)
        assertTrue(out.assets.cover!!.endsWith("boxFront.png"))
        root.deleteRecursively()
    }

    @Test fun userEditsWinOverImports() {
        val root = tmp(); val nes = File(root, "nes").apply { mkdirs() }
        File(nes, "a.nes").writeText("x")
        File(nes, "gamelist.xml").writeText("<gameList><game><path>./a.nes</path><name>Imported</name></game></gameList>")
        val g = Game("3", "Mine", "nes", path = File(nes, "a.nes").absolutePath, titleLocked = true)
        assertEquals("Mine", Gamelists.apply(listOf(g), listOf(RomFolder(root.path))).single().title)
        root.deleteRecursively()
    }


}
