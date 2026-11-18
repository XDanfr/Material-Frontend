package cc.xdan.frontend

import cc.xdan.frontend.input.ControllerManager
import cc.xdan.frontend.input.InputMaps
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.input.RetroButton
import cc.xdan.frontend.libretro.EmulationSession
import cc.xdan.frontend.libretro.Retro
import cc.xdan.frontend.model.Settings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class InputMapsTest {
    @Test fun gameOverridesBeatSystemOverrides() {
        var s = Settings()
        assertEquals(InputMaps.DEFAULT, InputMaps.resolve(s, "megadrive", "g1"))
        s = InputMaps.withMapping(s, InputMaps.systemKey("megadrive"), RetroButton.A, Pad.RB)
        s = InputMaps.withMapping(s, InputMaps.gameKey("g1"), RetroButton.A, Pad.NORTH)
        assertEquals(Pad.NORTH, InputMaps.resolve(s, "megadrive", "g1")[RetroButton.A.id], "game wins")
        assertEquals(Pad.RB, InputMaps.resolve(s, "megadrive", "g2")[RetroButton.A.id], "other games use the system map")
        assertEquals(Pad.EAST, InputMaps.resolve(s, "snes", "g3")[RetroButton.A.id], "other systems untouched")
        s = InputMaps.cleared(s, InputMaps.gameKey("g1"))
        assertEquals(Pad.RB, InputMaps.resolve(s, "megadrive", "g1")[RetroButton.A.id])
    }

    @Test fun remapAppliesToTheRunningGame() {
        val pads = ControllerManager()
        val session = EmulationSession(File("unused"), null, "g", pads, File("."), rewindEnabled = false)
        fun a(): Short { session.inputPoll(); return session.inputState(0, Retro.DEVICE_JOYPAD, 0, RetroButton.A.id) }
        pads.onKey(null, Pad.EAST, true)
        assertEquals(1, a().toInt(), "right face button is RetroPad A by default")
        session.setInputMap(InputMaps.DEFAULT.toMutableList().also { it[RetroButton.A.id] = Pad.SOUTH })
        assertEquals(0, a().toInt(), "after remapping, the right button no longer presses A")
        pads.onKey(null, Pad.EAST, false); pads.onKey(null, Pad.SOUTH, true)
        assertEquals(1, a().toInt(), "the bottom button now presses A")
        // The bitmask query (RETRO_DEVICE_ID_JOYPAD_MASK) agrees.
        session.inputPoll()
        val mask = session.inputState(0, Retro.DEVICE_JOYPAD, 0, Retro.DEVICE_ID_JOYPAD_MASK).toInt()
        assertEquals(1 shl RetroButton.A.id, mask and (1 shl RetroButton.A.id))
    }

    @Test fun choosingAPlayerSwapsPorts() {
        val pads = ControllerManager()
        pads.attachForTest(instance = 11, port = 0); pads.attachForTest(instance = 22, port = 1)
        pads.assignPort(22, 0)
        assertEquals(0, pads.playerOf(22)); assertEquals(1, pads.playerOf(11))
    }
}
