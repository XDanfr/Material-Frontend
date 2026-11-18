package cc.xdan.frontend

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.NavEvent
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.input.UiBindings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class UiBindingsTest {
    @Test fun customKeyWinsAndDefaultsRemain() {
        val custom = UiBindings.withKey(emptyMap(), Nav.CONFIRM, Key.K.keyCode)
        assertEquals(Nav.CONFIRM, UiBindings.navForKey(Key.K, custom))
        assertEquals(Nav.CONFIRM, UiBindings.navForKey(Key.Enter, custom), "defaults still work")
        // Re-binding Q (Previous tab by default) to Back moves it.
        val moved = UiBindings.withKey(custom, Nav.BACK, Key.Q.keyCode)
        assertEquals(Nav.BACK, UiBindings.navForKey(Key.Q, moved))
        assertNull(UiBindings.navForKey(Key.Z, moved))
        assertEquals(mapOf(Pad.NORTH to Nav.CONFIRM), UiBindings.padOverrides(UiBindings.withPad(emptyMap(), Nav.CONFIRM, Pad.NORTH)))
    }

    @Test fun bindingThroughTheKeyHandler() = runBlocking {
        System.getenv("MF_DATA_DIR") ?: return@runBlocking
        val app = AppController()
        app.updateSettings { it.copy(onboardingComplete = true, uiKeyBindings = emptyMap(), uiPadBindings = emptyMap()) }
        app.startBinding(Nav.SEARCH, "Search")
        handleKey(app, KeyEvent(Key.K, KeyEventType.KeyDown)); handleKey(app, KeyEvent(Key.K, KeyEventType.KeyUp))
        assertEquals(Key.K.keyCode, app.settings.value.uiKeyBindings["SEARCH"])
        assertNull(app.binding.value)
        // K now searches (Big Screen: keys drive navigation).
        app.mode.value = cc.xdan.frontend.model.UiMode.BIGSCREEN
        val got = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { app.controllers.events.collect { got += it } }
        handleKey(app, KeyEvent(Key.K, KeyEventType.KeyDown)); yield()
        assertEquals(Nav.SEARCH, got.single().nav)
        job.cancel()
        // A controller button binds the same way.
        app.startBinding(Nav.MENU, "Menu")
        app.controllers.padCapture!!.invoke(Pad.SELECT)
        assertEquals("SELECT", app.settings.value.uiPadBindings["MENU"])
        Thread.sleep(200)
        assertEquals(Nav.MENU, app.controllers.padBindings[Pad.SELECT], "applied to the controller manager")
        // Escape cancels without binding.
        app.startBinding(Nav.ALT, "Favourite")
        handleKey(app, KeyEvent(Key.Escape, KeyEventType.KeyDown))
        assertNull(app.settings.value.uiKeyBindings["ALT"]); assertNull(app.binding.value)
    }
}
