package cc.xdan.frontend

import kotlinx.coroutines.flow.drop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.isAltPressed
import cc.xdan.frontend.data.HostOs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.ui.App
import cc.xdan.frontend.ui.brand.IconRaster
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.Taskbar
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "Material Frontend")
    System.setProperty("skiko.vsync.enabled", "true")
    val app = AppController()
    if ("--bigscreen" in args) { app.mode.value = UiMode.BIGSCREEN; app.fullscreen.value = true }
    if ("--xmb" in args) { app.mode.value = UiMode.XMB; app.fullscreen.value = true }

    // Inside a packaged .app the bundle's Liquid Glass icon is used; elsewhere set the dock/taskbar icon.
    val iconPng = runCatching { IconRaster.png(IconRaster.appIconSvg(), 512) }.getOrNull()
    if (System.getProperty("jpackage.app-path") == null && iconPng != null) runCatching {
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE))
            Taskbar.getTaskbar().iconImage = ImageIO.read(ByteArrayInputStream(iconPng))
    }
    val windowIcon = iconPng?.let { BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap()) }

    application {
        // Start in the right placement; the sync below would otherwise read the initial Floating and turn fullscreen off.
        val state = rememberWindowState(width = 1440.dp, height = 900.dp,
            placement = if (app.fullscreen.value) WindowPlacement.Fullscreen else WindowPlacement.Floating)
        val fullscreen by app.fullscreen.collectAsState()
        LaunchedEffect(fullscreen) { state.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating }
        // Keep in sync when the OS changes it (macOS green button / Globe-F, window manager shortcuts).
        LaunchedEffect(Unit) { snapshotFlow { state.placement }.drop(1).collect { app.fullscreen.value = it == WindowPlacement.Fullscreen } }
        LaunchedEffect(Unit) { app.minimizeRequests.collect { state.isMinimized = it } }
        LaunchedEffect(Unit) { app.exitRequests.collect { app.shutdown(); exitApplication() } }

        // Compose 1.12 can trip over its own on-screen position bookkeeping ("LayoutNode … not found in RectList")
        // for a single frame. Skip that frame and keep going instead of showing a fatal error; anything else is
        // still reported the normal way.
        @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.window.LocalWindowExceptionHandlerFactory provides
            androidx.compose.ui.window.WindowExceptionHandlerFactory { w ->
                val fallback = androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory.exceptionHandler(w)
                androidx.compose.ui.window.WindowExceptionHandler { e ->
                    if (isComposeRectGlitch(e)) System.err.println("Skipped a frame: ${e.message}") else fallback.onException(e)
                }
            }) {
        Window(
            onCloseRequest = { app.shutdown(); exitApplication() },
            state = state,
            title = "Material Frontend",
            icon = windowIcon,
            onPreviewKeyEvent = { e -> handleKey(app, e) },
        ) {
            // Ignore controllers while another window is in front (a Steam game, a browser…).
            DisposableEffect(window) {
                val l = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { app.onWindowFocus(true) }
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) { app.onWindowFocus(false) }
                }
                window.addWindowFocusListener(l)
                app.controllers.windowFocused = window.isFocused
                onDispose { window.removeWindowFocusListener(l) }
            }
            App(app)
        }
        }
    }
}

/** The known Compose layout glitch (see Main): harmless to skip. */
internal fun isComposeRectGlitch(e: Throwable): Boolean =
    e is IllegalArgumentException && e.message?.contains("not found in RectList") == true

/** Control-Command-F on macOS (the system's own "Enter Full Screen" shortcut), F11 elsewhere. */
private fun isFullscreenShortcut(e: KeyEvent): Boolean =
    if (HostOs.current == HostOs.MAC) e.key == Key.F && e.isMetaPressed && e.isCtrlPressed && !e.isAltPressed
    else e.key == Key.F11

/**
 * Keyboard → controller bridge. In list mode the keyboard belongs to normal Compose focus; in
 * Big Screen / XMB / in-game it drives the same navigation events as a gamepad.
 */
/** The character a key types; falls back to the key code when AWT gives no char on key-down. */
private fun typedChar(e: KeyEvent): Char? {
    val c = e.utf16CodePoint.toChar()
    if (c.isLetterOrDigit() || c in " -:'&!.?") return c
    val code = e.key.keyCode.toInt()
    return when (code) { in 65..90 -> (code + 32).toChar(); in 48..57 -> code.toChar(); 45 -> '-'; else -> null }
}

internal fun handleKey(app: AppController, e: KeyEvent): Boolean {
    val down = e.type == KeyEventType.KeyDown
    if (down && isFullscreenShortcut(e)) { app.fullscreen.value = !app.fullscreen.value; return true }
    // Settings is waiting for a key to bind (Escape cancels).
    app.keyCapture?.let { cb ->
        if (down) { app.keyCapture = null; if (e.key != Key.Escape) cb(e.key) else app.cancelBinding() }
        return true
    }
    if (!app.settings.value.onboardingComplete) {
        // Setup: one navigation path for keyboard and controller (Backspace left alone for text fields).
        if (e.isCtrlPressed || e.isMetaPressed) return false
        val nav = when (e.key) {
            Key.DirectionUp -> Nav.UP; Key.DirectionDown -> Nav.DOWN; Key.DirectionLeft -> Nav.LEFT; Key.DirectionRight -> Nav.RIGHT
            Key.Enter -> Nav.CONFIRM; Key.Escape -> Nav.BACK; Key.Tab -> Nav.MENU
            else -> null
        } ?: return false
        if (down) app.controllers.onKey(nav, null, true)
        return true
    }
    val inGame = app.session.value != null
    if (!inGame && app.mode.value == UiMode.LIST) return false
    // Search owns the keyboard: Escape closes, Enter finishes, Backspace deletes, everything else types.
    app.textInput?.takeIf { !inGame }?.let { type ->
        if (e.isCtrlPressed || e.isMetaPressed) return false
        if (!down) return true
        val ch = when (e.key) {
            Key.Escape -> '\u001b'; Key.Backspace -> '\b'; Key.Enter, Key.NumPadEnter -> '\n'; Key.Spacebar -> ' '
            else -> typedChar(e)
        }
        ch?.let(type)
        return true
    }
    if (e.isCtrlPressed || e.isMetaPressed) return false

    if (inGame) {
        // Emulated pad on port 0.
        val pad = when (e.key) {
            Key.DirectionUp -> Pad.UP; Key.DirectionDown -> Pad.DOWN; Key.DirectionLeft -> Pad.LEFT; Key.DirectionRight -> Pad.RIGHT
            Key.Z -> Pad.SOUTH; Key.X -> Pad.EAST; Key.A -> Pad.WEST; Key.S -> Pad.NORTH
            Key.Q -> Pad.LB; Key.W -> Pad.RB; Key.One -> Pad.LT; Key.Three -> Pad.RT
            Key.Enter -> Pad.START; Key.ShiftRight, Key.ShiftLeft -> Pad.SELECT
            else -> null
        }
        val nav = when (e.key) {
            Key.Escape, Key.F1 -> Nav.GUIDE
            // When the quick menu is open (gameMode = false) the same keys navigate it.
            Key.DirectionUp -> Nav.UP; Key.DirectionDown -> Nav.DOWN; Key.DirectionLeft -> Nav.LEFT; Key.DirectionRight -> Nav.RIGHT
            Key.Enter, Key.Z -> Nav.CONFIRM; Key.X, Key.Backspace -> Nav.BACK
            else -> null
        }
        session@ run {
            val s = app.session.value ?: return@run
            if (e.key == Key.Tab) { s.fastForward = down; return true }
            if (e.key == Key.Backspace && app.controllers.gameMode) { app.controllers.keyboardRewind = down; return true }
            if (down && e.key == Key.F5) { s.saveState(1); return true }
            if (down && e.key == Key.F7) { s.loadState(1); return true }
        }
        app.controllers.onKey(nav, pad, down)
        return pad != null || nav != null
    }

    val nav = cc.xdan.frontend.input.UiBindings.navForKey(e.key, app.settings.value.uiKeyBindings) ?: return false
    if (!down) return true
    app.controllers.onKey(nav, null, true)
    return true
}
