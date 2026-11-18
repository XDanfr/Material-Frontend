package cc.xdan.frontend

import cc.xdan.frontend.data.SteamAppInfo
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.LogoPin
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LogoPinTest {
    @Test fun precedence() {
        val g = Game("g", "G", "steam", path = "1", defaultLogoPin = LogoPin.UPPER_CENTER)
        assertEquals(LogoPin.UPPER_CENTER, g.effectiveLogoPin(null), "Match Steam → the game's Steam default")
        assertEquals(LogoPin.CENTER, g.effectiveLogoPin(LogoPin.CENTER), "global setting applies to games without their own")
        val own = g.copy(logoPin = LogoPin.BOTTOM_CENTER)
        assertEquals(LogoPin.BOTTOM_CENTER, own.effectiveLogoPin(LogoPin.CENTER), "a game you set never follows the global setting")
        assertEquals(LogoPin.BOTTOM_LEFT, Game("h", "H", "snes", path = "/h").effectiveLogoPin(null))
        assertEquals(LogoPin.UPPER_CENTER, LogoPin.fromSteam("UpperCenter"))
    }

    @Test fun readsSteamLogoPositions() {
        val f = listOf("Library/Application Support/Steam/appcache/appinfo.vdf", ".steam/steam/appcache/appinfo.vdf")
            .map { File(System.getProperty("user.home"), it) }.firstOrNull { it.isFile } ?: return
        val apps = SteamAppInfo.read(f, null)
        val pins = apps.values.mapNotNull { it.logoPosition }
        println("appinfo: ${apps.size} apps, ${pins.size} with a logo position: ${pins.groupingBy { it }.eachCount()}")
        assert(pins.isEmpty() || pins.all { LogoPin.fromSteam(it) != null }) { "unknown pinned_position: ${pins.toSet()}" }
    }
}
