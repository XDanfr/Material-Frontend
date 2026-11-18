package cc.xdan.frontend

import cc.xdan.frontend.input.ControllerManager
import cc.xdan.frontend.input.Pad
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
