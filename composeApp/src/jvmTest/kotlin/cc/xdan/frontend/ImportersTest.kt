package cc.xdan.frontend

import cc.xdan.frontend.data.BinaryVdf
import cc.xdan.frontend.data.Gamelists
import cc.xdan.frontend.data.LauncherSources
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.RomFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test fun parsesBinaryShortcutsVdf() {
        val b = ByteArrayOutputStream()
        fun str(s: String) { b.write(s.toByteArray()); b.write(0) }
        b.write(0); str("shortcuts")
        b.write(0); str("0")
        b.write(2); str("appid"); b.write(byteArrayOf(0x78, 0x56, 0x34, 0x92.toByte()))
        b.write(1); str("AppName"); str("Nexus Launcher")
        b.write(1); str("Exe"); str("\"/home/me/Nexus.AppImage\"")
        b.write(8); b.write(8); b.write(8)
        @Suppress("UNCHECKED_CAST")
        val entry = (BinaryVdf.parse(b.toByteArray())["shortcuts"] as Map<String, Any>)["0"] as Map<String, Any>
        assertEquals("Nexus Launcher", entry["AppName"])
        assertEquals(0x92345678.toInt(), entry["appid"])
    }

    @Test fun readsEpicManifests() {
        val dir = tmp()
        File(dir, "a.item").writeText("""{"DisplayName":"Alan Wake 2","AppName":"Dodo","CatalogNamespace":"ns","CatalogItemId":"cid","AppCategories":["public","games","applications"],"bIsIncompleteInstall":false}""")
        File(dir, "b.item").writeText("""{"DisplayName":"Unreal Engine","AppName":"UE_5","AppCategories":["engines"]}""")
        File(dir, "c.item").writeText("""{"DisplayName":"Half","AppName":"Half","AppCategories":["games"],"bIsIncompleteInstall":true}""")
        val games = LauncherSources.epic(listOf(dir))
        assertEquals(listOf("Alan Wake 2"), games.map { it.title })
        assertEquals("com.epicgames.launcher://apps/ns%3Acid%3ADodo?action=launch&silent=true", games.single().path)
        dir.deleteRecursively()
    }

    @Test fun readsHeroicInstalls() {
        val dir = tmp()
        File(dir, "legendaryConfig/legendary").mkdirs()
        File(dir, "legendaryConfig/legendary/installed.json").writeText("""{"Fortnite":{"title":"Fortnite","install_path":"/g/F","is_dlc":false},"Dlc1":{"title":"DLC","is_dlc":true}}""")
        File(dir, "gog_store").mkdirs(); File(dir, "store_cache").mkdirs()
        File(dir, "gog_store/installed.json").writeText("""{"installed":[{"appName":"1207658924","install_path":"/g/Witcher"}]}""")
        File(dir, "store_cache/gog_library.json").writeText("""{"games":[{"app_name":"1207658924","title":"The Witcher"}]}""")
        val games = LauncherSources.heroic(listOf(dir)).associateBy { it.platformId }
        assertEquals("Fortnite", games["epic"]?.title)
        assertEquals("heroic://launch?appName=Fortnite&runner=legendary", games["epic"]?.path)
        assertEquals("The Witcher", games["gog"]?.title)
        assertEquals(2, games.size)
        dir.deleteRecursively()
    }

    @Test fun recognisesLaunchUris() {
        assertTrue(Launchers.isUri("heroic://launch?appName=x"))
        assertTrue(Launchers.isUri("lutris:rungameid/3"))
        assertTrue(Launchers.isUri("shell:AppsFolder\\Pkg_abc!Game"))
        assertFalse(Launchers.isUri("C:\\Games\\EA\\game.exe"))
        assertFalse(Launchers.isUri("/home/me/game.x86_64"))
    }

    private fun steamInstall(steam: File, vararg games: Pair<String, String>, libraries: List<String> = emptyList()) {
        File(steam, "steamapps").mkdirs()
        File(steam, "steamapps/libraryfolders.vdf").writeText(
            "\"libraryfolders\" {\n" + libraries.mapIndexed { i, p -> "\"$i\" { \"path\" \"${p.replace("\\", "\\\\")}\" }" }.joinToString("\n") + "\n}")
        games.forEach { (id, name) -> File(steam, "steamapps/appmanifest_$id.acf").writeText("\"AppState\" { \"appid\" \"$id\" \"name\" \"$name\" }") }
    }

    @Test fun findsSteamInCrossOverBottles() {
        val bottles = tmp(); val bottle = File(bottles, "Steam").apply { mkdirs() }
        val steam = File(bottle, "drive_c/Program Files (x86)/Steam")
        val dLib = tmp()   // a second library on D: (dosdevices/d: → elsewhere)
        steamInstall(steam, "620" to "Portal 2", "228980" to "Steamworks Common Redistributables", libraries = listOf("C:\\Program Files (x86)\\Steam", "D:\\SteamLibrary"))
        steamInstall(File(dLib, "SteamLibrary"), "1145360" to "Hades")
        File(bottle, "dosdevices").mkdirs()
        java.nio.file.Files.createSymbolicLink(File(bottle, "dosdevices/d:").toPath(), dLib.toPath())
        val games = cc.xdan.frontend.data.CompatSteam.crossover(bottles, File("/Applications/CrossOver.app/wine")).associateBy { it.title }
        assertEquals(setOf("Portal 2", "Hades"), games.keys, "tools are skipped, D: library is followed")
        val hades = games.getValue("Hades")
        assertEquals("1145360", hades.steamAppId)
        val cmd = (Launchers.options(hades, cc.xdan.frontend.model.Settings()).single().second as cc.xdan.frontend.emulators.LaunchTarget.External).command
        assertEquals(listOf("/Applications/CrossOver.app/wine", "--bottle", "Steam", "C:\\Program Files (x86)\\Steam\\steam.exe", "-applaunch", "1145360"), cmd)
        bottles.deleteRecursively(); dLib.deleteRecursively()
    }

    @Test fun findsSteamOnParallelsSharedDrives() {
        val volumes = tmp()
        val c = File(volumes, "[C] Windows 11").apply { mkdirs() }
        val d = File(volumes, "[D] Games").apply { mkdirs() }
        steamInstall(File(c, "Program Files (x86)/Steam"), "570" to "Dota 2", libraries = listOf("D:\\SteamLibrary"))
        steamInstall(File(d, "SteamLibrary"), "292030" to "The Witcher 3: Wild Hunt")
        val games = cc.xdan.frontend.data.CompatSteam.parallels(volumes, "/usr/local/bin/prlctl")
        assertEquals(setOf("Dota 2", "The Witcher 3: Wild Hunt"), games.map { it.title }.toSet())
        val witcher = games.first { it.title.startsWith("The Witcher") }
        val cmd = (Launchers.options(witcher, cc.xdan.frontend.model.Settings()).single().second as cc.xdan.frontend.emulators.LaunchTarget.External).command
        assertEquals(listOf("/usr/local/bin/prlctl", "exec", "Windows 11", "--current-user", "C:\\Program Files (x86)\\Steam\\steam.exe", "-applaunch", "292030"), cmd)
        volumes.deleteRecursively()
    }
}
