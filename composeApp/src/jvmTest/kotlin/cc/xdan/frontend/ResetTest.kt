package cc.xdan.frontend

import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.RomFolder
import cc.xdan.frontend.model.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reset actions keep what they promise to keep. Only runs against a scratch data folder
 * (MF_DATA_DIR) so it can never touch real user data.
 */
class ResetTest {
    @Test
    fun resetsKeepGamesSavesAndCores() {
        if (System.getenv("MF_DATA_DIR") == null) return
        val app = AppController()
        val save = File(AppPaths.saves, "reset-test.srm").apply { writeText("save") }
        val core = File(AppPaths.cores, "reset-test_libretro.dylib").apply { writeText("core") }
        val art = File(AppPaths.media, "snes/x/cover.png").apply { parentFile.mkdirs(); writeText("art") }

        app.updateSettings { it.copy(onboardingComplete = true, themeMode = ThemeMode.DARK, romFolders = listOf(RomFolder("/roms"))) }
        app.library.update { listOf(Game("g", "Game", "snes", path = "/roms/g.sfc", favorite = true)) }

        app.runSetupAgain()
        assertFalse(app.settings.value.onboardingComplete)
        assertEquals(ThemeMode.DARK, app.settings.value.themeMode, "setup again keeps settings")
        assertEquals(1, app.library.value.size)

        app.resetSettings()
        assertEquals(ThemeMode.SYSTEM, app.settings.value.themeMode)
        assertTrue(app.settings.value.romFolders.isEmpty())
        assertEquals(1, app.library.value.size, "reset settings keeps the library")

        app.clearLibrary()
        assertTrue(app.library.value.isEmpty())

        app.resetEverything()
        assertFalse(art.exists(), "downloaded artwork is removed")
        assertTrue(save.exists() && core.exists(), "saves and cores are kept")
        save.delete(); core.delete()
        app.shutdown()
    }
}
