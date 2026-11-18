package cc.xdan.frontend.input

import cc.xdan.frontend.model.Settings

/** RetroPad buttons by libretro id (RETRO_DEVICE_ID_JOYPAD_*). */
enum class RetroButton(val id: Int, val label: String) {
    B(0, "B"), Y(1, "Y"), SELECT(2, "Select"), START(3, "Start"), UP(4, "Up"), DOWN(5, "Down"), LEFT(6, "Left"), RIGHT(7, "Right"),
    A(8, "A"), X(9, "X"), L(10, "L"), R(11, "R"), L2(12, "L2"), R2(13, "R2"), L3(14, "L3"), R3(15, "R3"),
}

/**
 * Which physical (positional) pad button drives each RetroPad button. Defaults are positional
 * (SNES layout: B bottom, A right, Y left, X top), which already suits 6-button Genesis (bottom row
 * A/B/C = Y/B/A, top row X/Y/Z = L/X/R). Overrides: per system ("system:<id>"), then per game ("game:<id>").
 */
object InputMaps {
    val DEFAULT: List<Pad> = listOf(Pad.SOUTH, Pad.WEST, Pad.SELECT, Pad.START, Pad.UP, Pad.DOWN, Pad.LEFT, Pad.RIGHT,
        Pad.EAST, Pad.NORTH, Pad.LB, Pad.RB, Pad.LT, Pad.RT, Pad.L3, Pad.R3)

    /** Buttons offered for remapping (the D-pad stays fixed so menus stay usable). */
    val remappable = listOf(RetroButton.B, RetroButton.A, RetroButton.Y, RetroButton.X, RetroButton.L, RetroButton.R,
        RetroButton.L2, RetroButton.R2, RetroButton.SELECT, RetroButton.START, RetroButton.L3, RetroButton.R3)

    fun systemKey(platformId: String) = "system:$platformId"
    fun gameKey(gameId: String) = "game:$gameId"

    fun resolve(s: Settings, platformId: String, gameId: String): List<Pad> {
        val map = DEFAULT.toMutableList()
        listOf(systemKey(platformId), gameKey(gameId)).forEach { key ->
            s.inputMaps[key]?.forEach { (id, pad) -> id.toIntOrNull()?.let { i -> Pad.entries.firstOrNull { it.name == pad }?.let { if (i in map.indices) map[i] = it } } }
        }
        return map
    }

    fun withMapping(s: Settings, key: String, button: RetroButton, pad: Pad): Settings {
        val m = (s.inputMaps[key] ?: emptyMap()) + (button.id.toString() to pad.name)
        return s.copy(inputMaps = s.inputMaps + (key to m))
    }

    fun cleared(s: Settings, key: String): Settings = s.copy(inputMaps = s.inputMaps - key)
}
