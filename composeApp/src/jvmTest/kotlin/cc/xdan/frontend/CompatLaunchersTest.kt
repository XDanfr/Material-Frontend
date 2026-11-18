package cc.xdan.frontend

import cc.xdan.frontend.data.CompatLaunchers
import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.WineRegistry
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatLaunchersTest {
    @Test fun publisherIdMatchesWindows() {
        // Known: Microsoft.WindowsCalculator_8wekyb3d8bbwe
        assertEquals("8wekyb3d8bbwe", CompatLaunchers.publisherId("CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US"))
    }

    @Test fun wineRegistryParses() {
        val reg = WineRegistry.parse("""
            WINE REGISTRY Version 2
            [Software\\Wow6432Node\\Ubisoft\\Launcher\\Installs\\635] 1700000000
            #time=1d
            "InstallDir"="C:/Program Files (x86)/Ubisoft/Ubisoft Game Launcher/games/Far Cry 3/"
        """.trimIndent())
        assertEquals("C:/Program Files (x86)/Ubisoft/Ubisoft Game Launcher/games/Far Cry 3/",
            reg["Software\\Wow6432Node\\Ubisoft\\Launcher\\Installs\\635"]?.get("InstallDir"))
    }

    @Test fun findsLaunchersInABottle() {
        if (HostOs.current == HostOs.WINDOWS) return
        val bottles = Files.createTempDirectory("bottles").toFile()
        val b = File(bottles, "Games"); val c = File(b, "drive_c")
        // Epic
        File(c, "ProgramData/Epic/EpicGamesLauncher/Data/Manifests").apply { mkdirs() }.resolve("a.item").writeText(
            """{"AppName":"Fortnite","DisplayName":"Fortnite","CatalogNamespace":"fn","CatalogItemId":"x","AppCategories":["public","games"]}""")
        // GOG
        File(c, "GOG Games/Witcher").apply { mkdirs() }.also { d ->
            File(d, "witcher.exe").writeText("x")
            File(d, "goggame-1207658924.info").writeText("""{"gameId":"1207658924","rootGameId":"1207658924","name":"The Witcher","playTasks":[{"isPrimary":true,"type":"FileTask","path":"witcher.exe"}]}""")
        }
        // Ubisoft (registry)
        File(b, "system.reg").writeText("WINE REGISTRY Version 2\n[Software\\\\Wow6432Node\\\\Ubisoft\\\\Launcher\\\\Installs\\\\635] 1\n\"InstallDir\"=\"C:/Ubisoft/games/Far Cry 3/\"\n")
        // EA
        File(c, "Program Files/EA Games/Unravel").apply { mkdirs() }.also { d ->
            File(d, "Unravel.exe").writeText("x")
            File(d, "__Installer").mkdirs(); File(d, "__Installer/installerdata.xml").writeText(
                "<DiPManifest><gameTitles><gameTitle locale=\"en_US\">Unravel</gameTitle></gameTitles><runtime><launcher><filePath>[HKEY_LOCAL_MACHINE\\SOFTWARE\\EA\\Unravel\\Install Dir]Unravel.exe</filePath></launcher></runtime></DiPManifest>")
        }
        try {
            val games = CompatLaunchers.crossoverEnvs(bottles, File("/fake/wine")).flatMap(CompatLaunchers::scan)
            val byPlatform = games.associateBy { it.platformId }
            assertEquals(setOf("epic", "gog", "ubisoft", "ea"), byPlatform.keys)
            assertTrue(byPlatform.values.all { it.id.startsWith("crossover-") && it.path == "/fake/wine" })
            assertTrue(byPlatform["epic"]!!.launchArgs!!.contains("start.exe \"com.epicgames.launcher://apps/"))
            assertEquals("--bottle \"Games\" start.exe /d \"C:\\GOG Games\\Witcher\" \"C:\\GOG Games\\Witcher\\witcher.exe\"", byPlatform["gog"]!!.launchArgs)
            assertTrue(byPlatform["ubisoft"]!!.launchArgs!!.endsWith("\"uplay://launch/635/0\""))
            assertEquals("Far Cry 3", byPlatform["ubisoft"]!!.title)
            assertTrue(byPlatform["ea"]!!.launchArgs!!.endsWith("\"C:\\Program Files\\EA Games\\Unravel\\Unravel.exe\""))
        } finally { bottles.deleteRecursively() }
    }

    @Test fun findsXboxGamesOnParallelsDrives() {
        if (HostOs.current == HostOs.WINDOWS) return
        val vols = Files.createTempDirectory("vols").toFile()
        File(vols, "[C] Windows 11/Users").mkdirs()
        File(vols, "[D] Windows 11/XboxGames/Forza Horizon 5/Content").apply { mkdirs() }.resolve("MicrosoftGame.config").writeText(
            """<Game configVersion="1"><Identity Name="Microsoft.SunriseBaseGame" Publisher="CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US" Version="1.0.0.0"/>
               <ExecutableList><Executable Name="ForzaHorizon5.exe" Id="Game"/></ExecutableList><ShellVisuals DefaultDisplayName="Forza Horizon 5"/></Game>""")
        try {
            val g = CompatLaunchers.parallelsEnvs(vols, "prlctl").flatMap(CompatLaunchers::scan).single()
            assertEquals("Forza Horizon 5", g.title); assertEquals("xbox-pc", g.platformId)
            assertEquals("exec \"Windows 11\" --current-user explorer.exe \"shell:AppsFolder\\Microsoft.SunriseBaseGame_8wekyb3d8bbwe!Game\"", g.launchArgs)
        } finally { vols.deleteRecursively() }
    }
}
