package cc.xdan.frontend.input

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lwjgl.sdl.SDLEvents
import org.lwjgl.sdl.SDLGamepad
import org.lwjgl.sdl.SDLHints
import org.lwjgl.sdl.SDLInit
import org.lwjgl.sdl.SDL_Event
import org.lwjgl.system.MemoryStack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Positional pad buttons (SDL naming: SOUTH = Xbox A / PS Cross / Nintendo B). */
enum class Pad { SOUTH, EAST, WEST, NORTH, LB, RB, LT, RT, SELECT, START, GUIDE, L3, R3, UP, DOWN, LEFT, RIGHT }

/** Semantic UI actions after applying the confirm/back convention. */
enum class Nav { UP, DOWN, LEFT, RIGHT, CONFIRM, BACK, ALT, SEARCH, MENU, OPTIONS, PREV_TAB, NEXT_TAB, PREV_PAGE, NEXT_PAGE, GUIDE }

enum class ControllerFamily(val label: String) {
    XBOX("Xbox"), PLAYSTATION("PlayStation"), NINTENDO("Nintendo"), STEAM("Steam"), GENERIC("Gamepad"), KEYBOARD("Keyboard")
}

data class ControllerInfo(val instanceId: Int, val name: String, val family: ControllerFamily, val typeLabel: String, val vendor: Int, val product: Int, val battery: Int?)

data class NavEvent(val nav: Nav, val pad: Pad?, val repeat: Boolean, val family: ControllerFamily)

/** Snapshot read by the libretro host each frame. */
class PadState {
    @Volatile var buttons: Int = 0           // bit per Pad.ordinal
    @Volatile var lx: Short = 0; @Volatile var ly: Short = 0
    @Volatile var rx: Short = 0; @Volatile var ry: Short = 0
    @Volatile var lt: Short = 0; @Volatile var rt: Short = 0
    fun pressed(p: Pad) = buttons and (1 shl p.ordinal) != 0
}

/**
 * SDL3 gamepad backend on its own thread + keyboard bridge. Emits [NavEvent]s with key-repeat for
 * directions, tracks connected pads and their family (for glyphs and default layouts).
 */
class ControllerManager {
    private val _controllers = MutableStateFlow<List<ControllerInfo>>(emptyList())
    val controllers: StateFlow<List<ControllerInfo>> = _controllers.asStateFlow()

    private val _activeFamily = MutableStateFlow(ControllerFamily.KEYBOARD)
    /** Family of the most recently used input device — drives button glyphs. */
    val activeFamily: StateFlow<ControllerFamily> = _activeFamily.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    private val _connected = MutableSharedFlow<ControllerInfo>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Fires when a new pad is plugged in (used for auto Big Screen). */
    val connected: SharedFlow<ControllerInfo> = _connected.asSharedFlow()

    @Volatile var nintendoConfirm = false
    /** When true, pads drive the emulated game; only hotkeys produce nav events. */
    @Volatile var gameMode = false
    /** Keyboard rewind (Backspace held in game). */
    @Volatile var keyboardRewind = false

    /** Port -> state. Port 0 merges keyboard and first pad. */
    val ports = Array(4) { PadState() }
    val keyboard = PadState()

    private val handles = ConcurrentHashMap<Int, Long>()   // instance id -> SDL_Gamepad*
    private val portOf = ConcurrentHashMap<Int, Int>()
    @Volatile private var running = false
    @Volatile var sdlAvailable = false; private set

    fun start() {
        if (running) return
        running = true
        Thread({ loop() }, "sdl-input").apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    private fun loop() {
        try {
            SDLHints.SDL_SetHint(SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1")
            if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_GAMEPAD)) { System.err.println("SDL gamepad init failed"); return }
            sdlAvailable = true
        } catch (t: Throwable) {
            System.err.println("SDL unavailable: ${t.message}"); return
        }
        val repeat = HashMap<Pair<Int, Pad>, Long>()
        val prev = HashMap<Int, Int>()
        val event = SDL_Event.calloc()
        try {
            while (running) {
                while (SDLEvents.SDL_PollEvent(event)) {
                    when (event.type()) {
                        SDLEvents.SDL_EVENT_GAMEPAD_ADDED -> open(event.gdevice().which())
                        SDLEvents.SDL_EVENT_GAMEPAD_REMOVED -> close(event.gdevice().which())
                    }
                }
                SDLGamepad.SDL_UpdateGamepads()
                val now = System.nanoTime()
                for ((id, h) in handles) {
                    val state = readState(h)
                    val port = portOf[id] ?: continue
                    ports[port].apply {
                        buttons = state.buttons; lx = state.lx; ly = state.ly; rx = state.rx; ry = state.ry; lt = state.lt; rt = state.rt
                    }
                    val before = prev[id] ?: 0
                    val family = _controllers.value.firstOrNull { it.instanceId == id }?.family ?: ControllerFamily.GENERIC
                    for (p in Pad.entries) {
                        val bit = 1 shl p.ordinal
                        val down = state.buttons and bit != 0
                        val wasDown = before and bit != 0
                        val key = id to p
                        if (down && !wasDown) {
                            _activeFamily.value = family
                            emit(p, false, family)
                            repeat[key] = now + 380_000_000L
                        } else if (down && p in directional) {
                            val at = repeat[key] ?: continue
                            if (now >= at) { emit(p, true, family); repeat[key] = now + 70_000_000L }
                        } else if (!down) repeat.remove(key)
                    }
                    prev[id] = state.buttons
                }
                Thread.sleep(8)
            }
        } finally {
            event.free()
            handles.values.forEach { SDLGamepad.SDL_CloseGamepad(it) }
            SDLInit.SDL_Quit()
        }
    }

    private val directional = setOf(Pad.UP, Pad.DOWN, Pad.LEFT, Pad.RIGHT, Pad.LB, Pad.RB)

    private fun open(instance: Int) {
        val h = SDLGamepad.SDL_OpenGamepad(instance)
        if (h == 0L) return
        handles[instance] = h
        val port = (0 until 4).firstOrNull { p -> portOf.values.none { it == p } } ?: 3
        portOf[instance] = port
        val type = SDLGamepad.SDL_GetGamepadType(h)
        val name = SDLGamepad.SDL_GetGamepadName(h) ?: "Gamepad"
        val vendor = SDLGamepad.SDL_GetGamepadVendor(h).toInt() and 0xffff
        val product = SDLGamepad.SDL_GetGamepadProduct(h).toInt() and 0xffff
        val battery = MemoryStack.stackPush().use { s ->
            val pct = s.mallocInt(1); SDLGamepad.SDL_GetGamepadPowerInfo(h, pct); pct.get(0).takeIf { it >= 0 }
        }
        val (family, label) = classify(type, name, vendor)
        val info = ControllerInfo(instance, name, family, label, vendor, product, battery)
        _controllers.value = _controllers.value + info
        _activeFamily.value = family
        _connected.tryEmit(info)
    }

    private fun close(instance: Int) {
        handles.remove(instance)?.let { SDLGamepad.SDL_CloseGamepad(it) }
        portOf.remove(instance)?.let { ports[it].buttons = 0 }
        _controllers.value = _controllers.value.filterNot { it.instanceId == instance }
        if (_controllers.value.isEmpty()) _activeFamily.value = ControllerFamily.KEYBOARD
    }

    private fun classify(type: Int, name: String, vendor: Int): Pair<ControllerFamily, String> {
        val n = name.lowercase()
        return when {
            n.contains("steam") || vendor == 0x28de -> ControllerFamily.STEAM to "Steam Controller / Deck"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_PS5 -> ControllerFamily.PLAYSTATION to "DualSense"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_PS4 -> ControllerFamily.PLAYSTATION to "DualShock 4"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_PS3 -> ControllerFamily.PLAYSTATION to "DualShock 3"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_XBOXONE -> ControllerFamily.XBOX to "Xbox One / Series"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_XBOX360 -> ControllerFamily.XBOX to "Xbox 360"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO -> ControllerFamily.NINTENDO to "Switch Pro"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR -> ControllerFamily.NINTENDO to "Joy-Con (pair)"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_LEFT || type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_RIGHT -> ControllerFamily.NINTENDO to "Joy-Con"
            type == SDLGamepad.SDL_GAMEPAD_TYPE_GAMECUBE -> ControllerFamily.NINTENDO to "GameCube adapter"
            n.contains("8bitdo") -> (if (n.contains("pro") || n.contains("sn30")) ControllerFamily.NINTENDO else ControllerFamily.XBOX) to "8BitDo"
            else -> ControllerFamily.GENERIC to "Generic gamepad"
        }
    }

    private data class Raw(val buttons: Int, val lx: Short, val ly: Short, val rx: Short, val ry: Short, val lt: Short, val rt: Short)

    private fun readState(h: Long): Raw {
        var b = 0
        fun set(p: Pad, v: Boolean) { if (v) b = b or (1 shl p.ordinal) }
        fun btn(i: Int) = SDLGamepad.SDL_GetGamepadButton(h, i)
        set(Pad.SOUTH, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH)); set(Pad.EAST, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_EAST))
        set(Pad.WEST, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_WEST)); set(Pad.NORTH, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH))
        set(Pad.LB, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER)); set(Pad.RB, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER))
        set(Pad.SELECT, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_BACK)); set(Pad.START, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_START))
        set(Pad.GUIDE, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE))
        set(Pad.L3, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK)); set(Pad.R3, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK))
        val lx = SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX)
        val ly = SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY)
        val lt = SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER)
        val rt = SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER)
        set(Pad.LT, lt > 16000); set(Pad.RT, rt > 16000)
        // Left stick doubles as D-pad for navigation.
        val dz = 20000
        set(Pad.UP, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP) || (ly < -dz && abs(ly.toInt()) > abs(lx.toInt())))
        set(Pad.DOWN, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN) || (ly > dz && abs(ly.toInt()) > abs(lx.toInt())))
        set(Pad.LEFT, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT) || (lx < -dz && abs(lx.toInt()) >= abs(ly.toInt())))
        set(Pad.RIGHT, btn(SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT) || (lx > dz && abs(lx.toInt()) >= abs(ly.toInt())))
        return Raw(b, lx, ly, SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX), SDLGamepad.SDL_GetGamepadAxis(h, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY), lt, rt)
    }

    private fun emit(p: Pad, repeat: Boolean, family: ControllerFamily) {
        val confirmPad = if (nintendoConfirm) Pad.EAST else Pad.SOUTH
        val backPad = if (nintendoConfirm) Pad.SOUTH else Pad.EAST
        val nav = when (p) {
            Pad.UP -> Nav.UP; Pad.DOWN -> Nav.DOWN; Pad.LEFT -> Nav.LEFT; Pad.RIGHT -> Nav.RIGHT
            confirmPad -> Nav.CONFIRM; backPad -> Nav.BACK
            Pad.WEST -> Nav.ALT; Pad.NORTH -> Nav.SEARCH
            Pad.START -> Nav.MENU; Pad.SELECT -> Nav.OPTIONS; Pad.GUIDE -> Nav.GUIDE
            Pad.LB -> Nav.PREV_TAB; Pad.RB -> Nav.NEXT_TAB; Pad.LT -> Nav.PREV_PAGE; Pad.RT -> Nav.NEXT_PAGE
            else -> return
        }
        if (gameMode && nav != Nav.GUIDE) {
            // In-game: Select+Start acts as Guide for pads without a home button.
            if (!(p == Pad.START && ports.any { it.pressed(Pad.SELECT) })) return
            _events.tryEmit(NavEvent(Nav.GUIDE, p, false, family)); return
        }
        _events.tryEmit(NavEvent(nav, p, repeat, family))
    }

    /** Keyboard bridge (called from the Compose window). Returns true if consumed as navigation. */
    fun onKey(nav: Nav?, pad: Pad?, down: Boolean): Boolean {
        if (pad != null) {
            val bit = 1 shl pad.ordinal
            keyboard.buttons = if (down) keyboard.buttons or bit else keyboard.buttons and bit.inv()
        }
        if (down && nav != null && (!gameMode || nav == Nav.GUIDE)) {
            _activeFamily.value = ControllerFamily.KEYBOARD
            _events.tryEmit(NavEvent(nav, pad, false, ControllerFamily.KEYBOARD))
            return true
        }
        return false
    }

    /** Input for emulated port (keyboard is merged into port 0). */
    fun portButtons(port: Int): Int = ports[port].buttons or (if (port == 0) keyboard.buttons else 0)

    fun rumble(port: Int, low: Int, high: Int, ms: Int) {
        val id = portOf.entries.firstOrNull { it.value == port }?.key ?: return
        val h = handles[id] ?: return
        runCatching { SDLGamepad.SDL_RumbleGamepad(h, low.toShort(), high.toShort(), ms) }
    }
}
