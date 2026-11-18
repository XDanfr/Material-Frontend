package cc.xdan.frontend

import cc.xdan.frontend.input.ControllerManager
import cc.xdan.frontend.input.Pad
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusAndRenameTest {
    @Test fun unfocusedWindowIgnoresPadInput() {
        val c = ControllerManager()
        c.onKey(null, Pad.SOUTH, true)
        assertTrue(c.portButtons(0) != 0)
        c.windowFocused = false
        assertEquals(0, c.portButtons(0), "game gets no input while another app is in front")
        c.windowFocused = true
        assertTrue(c.portButtons(0) != 0)
    }

    @Test fun lowBatteryWarnsOnce() {
        val c = ControllerManager()
        c.attachForTest(7, 0, cc.xdan.frontend.input.ControllerInfo(7, "Pad", cc.xdan.frontend.input.ControllerFamily.PLAYSTATION, "DualSense", 0, 0, 80))
        val got = ArrayList<Int?>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch { c.lowBattery.collect { got += it.battery } }
        c.updateBattery(7, 40); c.updateBattery(7, 14); c.updateBattery(7, 10); c.updateBattery(7, 30); c.updateBattery(7, 12)
        job.cancel()
        assertEquals(listOf<Int?>(14, 12), got, "warn at 15%, not again until charged past 25%")
        assertEquals(12, c.controllers.value.single().battery)
    }

    @Test fun appFocusDrivesControllers() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = AppController()
        app.onWindowFocus(false); assertFalse(app.controllers.windowFocused)
        app.onWindowFocus(true); assertTrue(app.controllers.windowFocused)
    }

    @Test fun renameRefetchOnlyWithTitleSearchProviders() {
        System.getenv("MF_DATA_DIR") ?: return
        val app = AppController()
        app.updateSettings { it.copy(scraper = it.scraper.copy(steamGridDbKey = "", igdbClientId = "", igdbClientSecret = "")) }
        assertFalse(app.scraper.titleSearchConfigured())
        app.updateSettings { it.copy(scraper = it.scraper.copy(steamGridDbKey = "k")) }
        assertTrue(app.scraper.titleSearchConfigured())
        app.updateSettings { it.copy(scraper = it.scraper.copy(steamGridDbKey = "")) }
    }
}
