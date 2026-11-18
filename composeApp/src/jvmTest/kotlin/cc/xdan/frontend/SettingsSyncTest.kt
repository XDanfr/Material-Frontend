package cc.xdan.frontend

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.model.ThemeMode
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.Usage
import cc.xdan.frontend.ui.settings.SettingsGroup
import cc.xdan.frontend.ui.settings.SettingsSection
import cc.xdan.frontend.ui.settings.ToggleSetting
import cc.xdan.frontend.ui.settings.ChoiceSetting
import cc.xdan.frontend.ui.settings.activateSetting
import cc.xdan.frontend.ui.settings.settingsGroups
import cc.xdan.frontend.ui.theme.LocalApp
import cc.xdan.frontend.ui.theme.LocalArtworkSeed
import cc.xdan.frontend.ui.theme.MaterialFrontendTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** All three modes render the same settings (except their own theme section) and edit the same values. */
class SettingsSyncTest {
    private fun groups(app: AppController, mode: UiMode): List<SettingsGroup> {
        var out: List<SettingsGroup> = emptyList()
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            CompositionLocalProvider(LocalApp provides app, LocalArtworkSeed provides remember { mutableStateOf(null) }) {
                MaterialFrontendTheme(app.settings.value, null) { out = settingsGroups(mode) }
            }
        }
        scene.render(); scene.close()
        return out
    }

    private fun keys(g: List<SettingsGroup>, exceptTheme: Boolean = true) =
        g.filter { !exceptTheme || it.section != SettingsSection.THEME }.flatMap { gr -> gr.items.map { "${gr.section}:${it.key}" } }

    @Test
    fun sameSettingsEverywhere() {
        if (System.getenv("MF_DATA_DIR") == null) return
        val app = AppController()
        app.updateSettings { it.copy(usage = Usage.BOTH, themeMode = ThemeMode.SYSTEM) }
        val list = groups(app, UiMode.LIST); val big = groups(app, UiMode.BIGSCREEN); val xmb = groups(app, UiMode.XMB)
        assertEquals(keys(list), keys(big)); assertEquals(keys(list), keys(xmb))
        // Theme-specific sections differ per mode.
        val theme = { g: List<SettingsGroup> -> g.first { it.section == SettingsSection.THEME }.items.map { it.key } }
        assertTrue(theme(list) != theme(big) && theme(big) != theme(xmb))

        // A controller press in one mode changes the shared value.
        val dark = big.first { it.section == SettingsSection.GENERAL }.items.first { it.key == "theme" } as ChoiceSetting
        activateSetting(dark, Nav.RIGHT, null, {}, {}, SettingsSection.GENERAL)
        assertEquals(ThemeMode.LIGHT, app.settings.value.themeMode)
        val motion = xmb.first { it.section == SettingsSection.GENERAL }.items.first { it.key == "reduceMotion" } as ToggleSetting
        activateSetting(motion, Nav.CONFIRM, null, {}, {}, SettingsSection.GENERAL)
        assertTrue(app.settings.value.reduceMotion)
        // …and every mode now shows it.
        assertTrue((groups(app, UiMode.LIST).first { it.section == SettingsSection.GENERAL }.items.first { it.key == "reduceMotion" } as ToggleSetting).value)

        // PC-games-only users don't see emulation sections.
        app.updateSettings { it.copy(usage = Usage.GAMES) }
        val sections = groups(app, UiMode.LIST).map { it.section }
        assertFalse(SettingsSection.BIOS in sections || SettingsSection.CORES in sections || SettingsSection.EMULATORS in sections)
        app.shutdown()
    }
}
