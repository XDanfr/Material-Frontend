package cc.xdan.frontend

import cc.xdan.frontend.data.CompatSteam
import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.InstallTarget
import cc.xdan.frontend.data.Installs
import cc.xdan.frontend.data.SteamAppInfo
import cc.xdan.frontend.data.SteamOwned
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OwnedLibraryTest {
    @Test fun installRulesPerHost() {
        val win = setOf("windows"); val mac = setOf("windows", "macos"); val lin = setOf("linux")
        assertEquals(listOf(InstallTarget.HostSteam), Installs.targets(win, HostOs.LINUX, emptyList(), emptyList()), "Linux installs Windows games (Proton)")
        assertEquals(listOf(InstallTarget.HostSteam), Installs.targets(lin, HostOs.LINUX, emptyList(), emptyList()))
        assertEquals(emptyList(), Installs.targets(lin, HostOs.WINDOWS, emptyList(), emptyList()))
        assertEquals(emptyList(), Installs.targets(win, HostOs.MAC, emptyList(), emptyList()), "Mac without CrossOver/Parallels can't install Windows-only")
        assertEquals(listOf(InstallTarget.CrossOver("Steam"), InstallTarget.Parallels("Windows 11")),
            Installs.targets(win, HostOs.MAC, listOf("Steam"), listOf("Windows 11")))
        assertEquals(InstallTarget.HostSteam, Installs.targets(mac, HostOs.MAC, listOf("Steam"), emptyList()).first(), "native Mac build first")
        val (prog, args) = Installs.command(InstallTarget.CrossOver("Steam"), "620")!!
        assertTrue(prog.endsWith("wine")); assertEquals(listOf("--bottle", "Steam", CompatSteam.STEAM_EXE, "steam://install/620"), args)
    }

    @Test fun ownedMinusInstalledKeepsOnlyGames() {
        val owned = SteamOwned.parseOwned("""{"response":{"game_count":4,"games":[
            {"appid":620,"name":"Portal 2","playtime_forever":90},{"appid":400,"name":"Portal"},
            {"appid":228980,"name":"Steamworks Common Redistributables"},{"appid":999,"name":"Some DLC"}]}}""")
        val info = mapOf("620" to SteamAppInfo.App("620", "Portal 2", "game", setOf("windows", "macos")),
            "999" to SteamAppInfo.App("999", "Some DLC", "dlc", emptySet()))
        val games = SteamOwned.notInstalled(owned, installedIds = setOf("400"), appInfo = info, root = null)
        assertEquals(listOf("steam-620"), games.map { it.id })
        val g = games.single()
        assertEquals(false, g.installed); assertEquals(setOf("windows", "macos"), g.os); assertEquals(90 * 60L, g.playTimeSeconds)
    }

    @Test fun localConfigAndSteamBottles() {
        val root = Files.createTempDirectory("steam").toFile()
        File(root, "userdata/12345/config").mkdirs()
        File(root, "userdata/12345/config/localconfig.vdf").writeText(
            "\"UserLocalConfigStore\"\n{\n\"Software\"\n{\n\"Valve\"\n{\n\"Steam\"\n{\n\"apps\"\n{\n\"620\"\n{\n\"LastPlayed\" \"1\"\n}\n\"400\"\n{\n}\n}\n}\n}\n}\n}\n")
        assertEquals(setOf("620", "400"), SteamOwned.localIds(root))
        assertEquals("76561197960278073", SteamOwned.steamId64("12345"))
        val bottles = Files.createTempDirectory("bottles").toFile()
        File(bottles, "Steam/drive_c/Program Files (x86)/Steam").mkdirs(); File(bottles, "Steam/drive_c/Program Files (x86)/Steam/steam.exe").writeText("x")
        File(bottles, "Office/drive_c").mkdirs()
        assertEquals(listOf("Steam"), CompatSteam.crossoverSteamBottles(bottles))
        root.deleteRecursively(); bottles.deleteRecursively()
    }

    /** A tiny v29 appinfo.vdf built by hand: string table + one app with common{name,type,oslist}. */
    @Test fun parsesBinaryAppInfo() {
        val strings = listOf("appinfo", "common", "name", "type", "oslist", "appid")
        val kv = ByteArrayOutputStream()
        fun i32(v: Int) = kv.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun str(s: String) { kv.write(s.toByteArray()); kv.write(0) }
        kv.write(0x00); i32(0)                       // appinfo {
        kv.write(0x02); i32(5); i32(620)             //   appid = 620
        kv.write(0x00); i32(1)                       //   common {
        kv.write(0x01); i32(2); str("Portal 2")
        kv.write(0x01); i32(3); str("Game")
        kv.write(0x01); i32(4); str("windows,macos,linux")
        kv.write(0x08); kv.write(0x08); kv.write(0x08) // } } end
        val entryHeader = 4 + 4 + 8 + 20 + 4 + 20
        val body = kv.toByteArray()
        val out = ByteArrayOutputStream()
        fun o32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        o32(0x07564429); o32(1)
        val headerLen = 4 + 4 + 8
        val appsLen = 4 + 4 + entryHeader + body.size + 4
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong((headerLen + appsLen).toLong()).array())
        o32(620); o32(entryHeader + body.size); out.write(ByteArray(entryHeader)); out.write(body)
        o32(0)
        o32(strings.size); strings.forEach { out.write(it.toByteArray()); out.write(0) }
        val apps = SteamAppInfo.parse(out.toByteArray())
        assertEquals(SteamAppInfo.App("620", "Portal 2", "game", setOf("windows", "macos", "linux")), apps["620"])
    }

    /** This Mac's real Steam data, read-only (skipped where there's no Steam). */
    @Test fun realSteamLibrary() {
        val root = File(System.getProperty("user.home"), "Library/Application Support/Steam").takeIf { File(it, "userdata").isDirectory } ?: return
        val owned = SteamOwned.localIds(root).map { SteamOwned.Owned(it, null) }
        val installed = cc.xdan.frontend.data.SteamLibrary().scan().map { it.path }.toSet()
        val info = SteamAppInfo.read(File(root, "appcache/appinfo.vdf"), owned.map { it.appId }.toSet())
        val games = SteamOwned.notInstalled(owned, installed, info, root)
        println("real Steam: ${owned.size} local apps, ${installed.size} installed, ${games.size} not installed; e.g. " +
            games.take(6).joinToString { "${it.title} ${it.os}" })
        assertTrue(games.none { it.path in installed })
    }
}
