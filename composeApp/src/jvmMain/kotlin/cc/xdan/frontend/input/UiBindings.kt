package cc.xdan.frontend.input

import androidx.compose.ui.input.key.Key

/**
 * The frontend's own controls (not in-game). Defaults below; users can add a key and/or a controller
 * button per action. A custom binding wins over a default that uses the same key/button.
 */
object UiBindings {
    /** Actions offered for remapping, with what they're called in Settings. */
    val actions: List<Pair<Nav, String>> = listOf(
        Nav.CONFIRM to "Select", Nav.BACK to "Back", Nav.ALT to "Favourite", Nav.SEARCH to "Search / options", Nav.MENU to "Menu",
        Nav.PREV_TAB to "Previous tab", Nav.NEXT_TAB to "Next tab", Nav.PREV_PAGE to "Previous page", Nav.NEXT_PAGE to "Next page",
        Nav.UP to "Up", Nav.DOWN to "Down", Nav.LEFT to "Left", Nav.RIGHT to "Right",
    )

    val defaultKeys: Map<Nav, List<Key>> = mapOf(
        Nav.UP to listOf(Key.DirectionUp), Nav.DOWN to listOf(Key.DirectionDown), Nav.LEFT to listOf(Key.DirectionLeft), Nav.RIGHT to listOf(Key.DirectionRight),
        Nav.CONFIRM to listOf(Key.Enter, Key.Spacebar), Nav.BACK to listOf(Key.Escape, Key.Backspace),
        Nav.ALT to listOf(Key.F), Nav.SEARCH to listOf(Key.Slash), Nav.MENU to listOf(Key.Tab), Nav.GUIDE to listOf(Key.F1),
        Nav.PREV_TAB to listOf(Key.Q), Nav.NEXT_TAB to listOf(Key.E),
        Nav.PREV_PAGE to listOf(Key.LeftBracket, Key.PageUp), Nav.NEXT_PAGE to listOf(Key.RightBracket, Key.PageDown),
    )

    /** Key → action: custom bindings first, then defaults. */
    fun navForKey(key: Key, custom: Map<String, Long>): Nav? =
        custom.entries.firstOrNull { it.value == key.keyCode }?.let { runCatching { Nav.valueOf(it.key) }.getOrNull() }
            ?: defaultKeys.entries.firstOrNull { key in it.value }?.key

    /** Pad → action overrides from settings (Nav name → Pad name). */
    fun padOverrides(custom: Map<String, String>): Map<Pad, Nav> = custom.mapNotNull { (n, p) ->
        val nav = runCatching { Nav.valueOf(n) }.getOrNull(); val pad = runCatching { Pad.valueOf(p) }.getOrNull()
        if (nav != null && pad != null) pad to nav else null
    }.toMap()

    fun keyName(code: Long): String = runCatching { java.awt.event.KeyEvent.getKeyText(code.toInt()) }.getOrDefault("Key $code")

    fun padName(p: Pad, family: ControllerFamily): String = when (p) {
        Pad.SOUTH -> when (family) { ControllerFamily.PLAYSTATION -> "Cross"; ControllerFamily.NINTENDO -> "B"; else -> "A" }
        Pad.EAST -> when (family) { ControllerFamily.PLAYSTATION -> "Circle"; ControllerFamily.NINTENDO -> "A"; else -> "B" }
        Pad.WEST -> when (family) { ControllerFamily.PLAYSTATION -> "Square"; ControllerFamily.NINTENDO -> "Y"; else -> "X" }
        Pad.NORTH -> when (family) { ControllerFamily.PLAYSTATION -> "Triangle"; ControllerFamily.NINTENDO -> "X"; else -> "Y" }
        Pad.LB -> if (family == ControllerFamily.PLAYSTATION) "L1" else "LB"
        Pad.RB -> if (family == ControllerFamily.PLAYSTATION) "R1" else "RB"
        Pad.LT -> if (family == ControllerFamily.PLAYSTATION) "L2" else "LT"
        Pad.RT -> if (family == ControllerFamily.PLAYSTATION) "R2" else "RT"
        Pad.START -> "Start"; Pad.SELECT -> "Select"; Pad.GUIDE -> "Home"
        Pad.UP -> "D-pad up"; Pad.DOWN -> "D-pad down"; Pad.LEFT -> "D-pad left"; Pad.RIGHT -> "D-pad right"
        else -> p.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** Settings with [nav] bound to [code] (and that key removed from any other action). */
    fun withKey(keys: Map<String, Long>, nav: Nav, code: Long) = keys.filterValues { it != code } + (nav.name to code)
    fun withPad(pads: Map<String, String>, nav: Nav, pad: Pad) = pads.filterValues { it != pad.name } + (nav.name to pad.name)
}
