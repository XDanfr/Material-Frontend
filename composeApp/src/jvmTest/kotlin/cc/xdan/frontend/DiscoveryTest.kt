package cc.xdan.frontend

import cc.xdan.frontend.emulators.DetectedEmulator
import cc.xdan.frontend.emulators.EmulatorCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryTest {
    @Test fun acceptingAndDecliningDiscoveries() {
        val app = AppController()
        val dolphin = EmulatorCatalog.all.first { it.id == "dolphin" }
        val pcsx2 = EmulatorCatalog.all.first { it.id == "pcsx2" }
        app.updateSettings { it.copy(knownLaunchers = setOf("steam"), importLaunchers = true) }
        app.testDiscoveries(listOf(
            AppController.Discovered("emu:/a/Dolphin", "Dolphin", "Emulator", DetectedEmulator(dolphin, "/a/Dolphin", "test")),
            AppController.Discovered("emu:/a/pcsx2", "PCSX2", "Emulator", DetectedEmulator(pcsx2, "/a/pcsx2", "test")),
            AppController.Discovered("launcher:epic", "Epic Games", "Launcher · 3 games"),
            AppController.Discovered("launcher:ea", "EA app", "Launcher · 1 game"),
        ))
        app.resolveDiscoveries(setOf("emu:/a/Dolphin", "launcher:epic"))
        val s = app.settings.value
        assertTrue(app.discoveries.value.isEmpty())
        assertEquals(listOf("/a/Dolphin"), s.emulators.map { it.path })
        assertTrue("emu:/a/pcsx2" in s.dismissedDiscoveries, "declined emulators aren't offered again")
        assertEquals(setOf("steam", "epic", "ea"), s.knownLaunchers)
        assertFalse("epic" in s.disabledLaunchers); assertTrue("ea" in s.disabledLaunchers)
    }
}
