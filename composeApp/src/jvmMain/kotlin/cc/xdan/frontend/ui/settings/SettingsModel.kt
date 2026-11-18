package cc.xdan.frontend.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.xdan.frontend.AppController
import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.emulators.Launchers
import cc.xdan.frontend.model.LibraryView
import cc.xdan.frontend.model.Settings
import cc.xdan.frontend.model.SortOrder
import cc.xdan.frontend.model.ThemeMode
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.Usage
import cc.xdan.frontend.model.VideoFilter
import cc.xdan.frontend.ui.theme.LocalApp

/*
 * One description of every setting, rendered three ways: the Settings screen (Library view),
 * the Settings tab in Big Screen and the Settings category in XMB. All three read and write the
 * same Settings, so a change anywhere shows everywhere. Complex editors (folders, emulators, cores,
 * API keys, BIOS) are desktop panels; controller modes show what they hold and point to them.
 */

sealed interface SettingItem {
    val key: String
    val title: String
    val subtitle: String?
}

data class ToggleSetting(override val key: String, override val title: String, override val subtitle: String?,
                         val value: Boolean, val onChange: (Boolean) -> Unit) : SettingItem

enum class ChoiceStyle { SEGMENTED, ACCENT }

data class ChoiceSetting(override val key: String, override val title: String, override val subtitle: String?,
                         val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit,
                         val style: ChoiceStyle = ChoiceStyle.SEGMENTED, val values: List<Long> = emptyList()) : SettingItem {
    val selectedLabel get() = labels.getOrNull(selected) ?: ""
}

data class ActionSetting(override val key: String, override val title: String, override val subtitle: String?,
                         val button: String, val run: () -> Unit, val destructive: Boolean = false, val confirm: String? = null) : SettingItem

data class InfoSetting(override val key: String, override val title: String, override val subtitle: String?) : SettingItem

/** Desktop-only editor; controller modes show [summary] instead. */
data class PanelSetting(override val key: String, override val title: String, override val subtitle: String?,
                        val summary: String, val panel: @Composable () -> Unit) : SettingItem

data class SettingsGroup(val section: SettingsSection, val items: List<SettingItem>)

/** Accent seeds with names (names are what controller modes show). */
val accentSeeds: List<Pair<String, Long>> = listOf(
    "Purple" to 0xFF6750A4, "Teal" to 0xFF006A6A, "Violet" to 0xFF8A4FFF, "Red" to 0xFFB3261E,
    "Green" to 0xFF386A20, "Amber" to 0xFFE8A33D, "Blue" to 0xFF0061A4, "Pink" to 0xFFC2185B,
)

/** Sections shown for this usage (emulation-only sections hidden for PC-games-only users). */
fun visibleSections(s: Settings): List<SettingsSection> = SettingsSection.entries.filter {
    !(s.usage == Usage.GAMES && it in setOf(SettingsSection.EMULATORS, SettingsSection.CORES, SettingsSection.PLAYBACK, SettingsSection.BIOS))
}

@Composable
fun settingsGroups(mode: UiMode): List<SettingsGroup> {
    val app = LocalApp.current
    val s by app.settings.state.collectAsState()
    val pads by app.controllers.controllers.collectAsState()
    val games by app.library.state.collectAsState()
    return visibleSections(s).map { section -> SettingsGroup(section, items(app, s, section, mode, pads.size, games.size)) }
}

private fun items(app: AppController, s: Settings, section: SettingsSection, mode: UiMode, padCount: Int, gameCount: Int): List<SettingItem> {
    fun set(fn: (Settings) -> Settings) = app.updateSettings(fn)
    fun onOff(b: Boolean) = if (b) "On" else "Off"
    return when (section) {
        SettingsSection.GENERAL -> listOf(
            when (mode) {
                UiMode.BIGSCREEN -> ActionSetting("userName", "Your name", s.userName.ifBlank { app.steamName?.let { "Using your Steam name: $it" } ?: "Used in the Home greeting" },
                    if (s.userName.isBlank()) "Set" else "Change", { app.nameEntry.value = true })
                else -> PanelSetting("userName", "Your name", "Used in the Big Screen greeting. Leave blank to use your Steam name.",
                    s.userName.ifBlank { app.steamName ?: "Not set" }) {
                    androidx.compose.material3.OutlinedTextField(s.userName, { v -> set { it.copy(userName = v.take(32)) } },
                        label = { androidx.compose.material3.Text("Name") }, singleLine = true,
                        placeholder = { app.steamName?.let { androidx.compose.material3.Text(it) } })
                }
            },
            ChoiceSetting("theme", "Light or dark", null, listOf("Automatic", "Light", "Dark", "Black"),
                ThemeMode.entries.indexOf(s.themeMode), { i -> set { it.copy(themeMode = ThemeMode.entries[i]) } }),
            ChoiceSetting("accent", "Accent colour", "Used when colour from artwork is off or there's no artwork",
                accentSeeds.map { it.first }, accentSeeds.indexOfFirst { it.second == s.seedColor }.coerceAtLeast(0),
                { i -> set { it.copy(seedColor = accentSeeds[i].second) } }, ChoiceStyle.ACCENT, accentSeeds.map { it.second }),
            ToggleSetting("artworkColour", "Colour from artwork", "Tint the interface from the focused game's box art", s.colorFromArtwork) { v -> set { it.copy(colorFromArtwork = v) } },
            ToggleSetting("reduceMotion", "Reduce motion", "Use the standard (non-bouncy) motion scheme", s.reduceMotion) { v -> set { it.copy(reduceMotion = v) } },
            ToggleSetting("launchAnimation", "Launch animation", "The logo animation when the app opens", s.launchAnimation) { v -> set { it.copy(launchAnimation = v) } },
            ChoiceSetting("startMode", "Open in", "The theme the app starts in", listOf("Library", "Big Screen", "XMB"),
                UiMode.entries.indexOf(s.defaultMode), { i -> set { it.copy(defaultMode = UiMode.entries[i], startFullscreen = UiMode.entries[i] != UiMode.LIST) } }),
            ToggleSetting("startFullscreen", "Start in fullscreen", null, s.startFullscreen) { v -> set { it.copy(startFullscreen = v) } },
            ChoiceSetting("heroStyle", "Hero art", "The wide artwork behind games", listOf("Sharp", "Blurred"), if (s.blurredHeroes) 1 else 0,
                { i -> set { it.copy(blurredHeroes = i == 1) } }),
            ChoiceSetting("logoPin", "Logo position", "On wide art · games you've set yourself keep their own",
                listOf("Match Steam") + cc.xdan.frontend.model.LogoPin.entries.map { it.label }, s.logoPin?.let { it.ordinal + 1 } ?: 0,
                { i -> set { it.copy(logoPin = if (i == 0) null else cc.xdan.frontend.model.LogoPin.entries[i - 1]) } }),
            ActionSetting("logoPinReset", "Reset logo position", if (s.logoPin == null) "Already using each game's default" else "Back to each game's Steam default",
                "Reset", { set { it.copy(logoPin = null) } }),
            ToggleSetting("pauseInBackground", "Pause games in the background", "When another app is in front, the built-in player pauses", s.pauseInBackground) { v -> set { it.copy(pauseInBackground = v) } },
            ToggleSetting("minimise", "Minimise while an external emulator runs", null, s.minimizeOnExternalLaunch) { v -> set { it.copy(minimizeOnExternalLaunch = v) } },
        )
        SettingsSection.THEME -> when (mode) {
            UiMode.LIST -> listOf(
                ChoiceSetting("libraryView", "Layout", null, listOf("Grid", "List"), LibraryView.entries.indexOf(s.libraryView),
                    { i -> set { it.copy(libraryView = LibraryView.entries[i]) } }),
                ChoiceSetting("sort", "Sort by", null, SortOrder.entries.map { it.label }, SortOrder.entries.indexOf(s.sort),
                    { i -> set { it.copy(sort = SortOrder.entries[i]) } }),
            )
            UiMode.BIGSCREEN -> listOf(
                ToggleSetting("bsClock", "Show clock", null, s.bigScreenClock) { v -> set { it.copy(bigScreenClock = v) } },
                ChoiceSetting("spotlight", "Spotlight shows", "Pick games for 'Chosen' from a game's More… menu",
                    cc.xdan.frontend.model.SpotlightMode.entries.map { it.label }, s.spotlight.ordinal,
                    { i -> set { it.copy(spotlight = cc.xdan.frontend.model.SpotlightMode.entries[i]) } }),
                ToggleSetting("bsHints", "Show button hints", null, s.bigScreenHints) { v -> set { it.copy(bigScreenHints = v) } },
            )
            UiMode.XMB -> listOf(
                ChoiceSetting("xmbBackground", "Background", "Shifts with the time of day",
                    cc.xdan.frontend.model.XmbBackground.entries.map { it.label },
                    cc.xdan.frontend.model.XmbBackground.entries.indexOf(if (s.xmbWaves) s.xmbBackground else cc.xdan.frontend.model.XmbBackground.OFF),
                    { i -> val b = cc.xdan.frontend.model.XmbBackground.entries[i]; set { it.copy(xmbBackground = b, xmbWaves = b != cc.xdan.frontend.model.XmbBackground.OFF) } }),
                ToggleSetting("xmbHints", "Show button hints", null, s.xmbHints) { v -> set { it.copy(xmbHints = v) } },
            )
        }
        SettingsSection.LIBRARY -> listOf(
            PanelSetting("folders", "Game folders", "Folders that hold your games",
                if (s.romFolders.isEmpty()) "No folders yet" else "${s.romFolders.size} folder(s)") { RomFoldersPanel() },
            ToggleSetting("steam", "Import Steam library", "Installed games and non-Steam shortcuts", s.importSteam) { v -> set { it.copy(importSteam = v) } },
            ToggleSetting("launchers", "Import other launchers", "Each one can be switched off below", s.importLaunchers) { v -> set { it.copy(importLaunchers = v) } },
        ) + cc.xdan.frontend.data.LauncherSources.Source.entries.map { src ->
            ToggleSetting("launcher-${src.id}", src.label, null, s.importLaunchers && src.id !in s.disabledLaunchers) { v ->
                set { it.copy(disabledLaunchers = if (v) it.disabledLaunchers - src.id else it.disabledLaunchers + src.id, importLaunchers = it.importLaunchers || v) }
            }
        } + listOf(
            ToggleSetting("gamelists", "Use existing ES-DE / Pegasus metadata", null, s.importGamelists) { v -> set { it.copy(importGamelists = v) } },
            ToggleSetting("notInstalled", "Show games you own but haven't installed", "Steam · greyed out, with Install", s.showNotInstalled) { v -> set { it.copy(showNotInstalled = v) } },
            ToggleSetting("rescanStartup", "Look for new games at start-up", null, s.rescanOnStartup) { v -> set { it.copy(rescanOnStartup = v) } },
            ActionSetting("rescan", "Rescan library", "$gameCount games", "Rescan", { app.rescan() }),
        )
        SettingsSection.EMULATORS -> listOf(
            PanelSetting("emulators", "Standalone emulators", "Detected and added emulators",
                "${s.emulators.count { it.enabled }} enabled") { EmulatorsPanel() },
            ActionSetting("scanEmulators", "Scan for emulators", null, "Scan", { app.scanEmulators() }),
        )
        SettingsSection.CORES -> listOf(
            PanelSetting("cores", "Cores", "Run retro systems inside the app", "Manage cores") { CoresPanel() },
            ActionSetting("installCores", "Install recommended cores", null, "Install",
                { app.installCores(recommendedCores().filter { !cc.xdan.frontend.emulators.CoreCatalog.isInstalled(it.id) }.map { it.id }) }),
        )
        SettingsSection.PLAYBACK -> listOf(
            ChoiceSetting("videoFilter", "Video", null, VideoFilter.entries.map { it.label }, VideoFilter.entries.indexOf(s.videoFilter),
                { i -> set { it.copy(videoFilter = VideoFilter.entries[i]) } }),
            ToggleSetting("integer", "Integer scaling", "Pixel-perfect sizes; may leave borders", s.integerScale) { v -> set { it.copy(integerScale = v) } },
            ToggleSetting("gpuInApp", "Run GPU cores in the app", "N64, PSP, Dreamcast and PS1 HW cores use OpenGL inside the app (new). Off = launch them through RetroArch.", s.gpuCoresInApp) { v -> set { it.copy(gpuCoresInApp = v) } },
            ToggleSetting("rewind", "Rewind", "Keeps a short history in memory", s.rewind) { v -> set { it.copy(rewind = v) } },
            ToggleSetting("autoSave", "Auto-save state on exit", "Resume exactly where you left off", s.autoSaveState) { v -> set { it.copy(autoSaveState = v) } },
        )
        SettingsSection.CONTROLLERS -> listOf(
            InfoSetting("pads", "Connected", if (padCount == 0) "No controllers — plug one in any time" else "$padCount controller(s)"),
            PanelSetting("padList", "Controllers", null, "${padCount} connected") { ControllersPanel() },
            ToggleSetting("nintendo", "Nintendo-style confirm", "Right button confirms, bottom goes back", s.nintendoConfirm) { v -> set { it.copy(nintendoConfirm = v) } },
            ToggleSetting("autoBig", "Open Big Screen when a controller connects", null, s.bigScreenOnController) { v -> set { it.copy(bigScreenOnController = v) } },
        ) + cc.xdan.frontend.input.UiBindings.actions.map { (nav, label) ->
            val keys = listOfNotNull(s.uiKeyBindings[nav.name]?.let { cc.xdan.frontend.input.UiBindings.keyName(it) }) +
                cc.xdan.frontend.input.UiBindings.defaultKeys[nav].orEmpty().filter { k -> k.keyCode !in s.uiKeyBindings.filterKeys { it != nav.name }.values }
                    .map { cc.xdan.frontend.input.UiBindings.keyName(it.keyCode) }
            val pad = s.uiPadBindings[nav.name]?.let { runCatching { cc.xdan.frontend.input.Pad.valueOf(it) }.getOrNull() }
            ActionSetting("bind-${nav.name}", "Control: $label",
                keys.distinct().joinToString(" / ") + (pad?.let { " · " + cc.xdan.frontend.input.UiBindings.padName(it, app.controllers.activeFamily.value) } ?: ""),
                "Change", { app.startBinding(nav, label) })
        } + listOf(
            ActionSetting("bindReset", "Reset controls", "Back to the default keys and buttons", "Reset",
                { set { it.copy(uiKeyBindings = emptyMap(), uiPadBindings = emptyMap()) } }, destructive = true),
        )
        SettingsSection.SCRAPING -> listOf(
            PanelSetting("keys", "Accounts and keys", "SteamGridDB and IGDB",
                if (s.scraper.steamGridDbKey.isBlank()) "Free sources only" else "SteamGridDB added") { ScraperPanel() },
            ToggleSetting("heroes", "Download hero art", "Wide banners for Big Screen and details", s.scraper.downloadHeroes) { v -> set { it.copy(scraper = it.scraper.copy(downloadHeroes = v)) } },
            ToggleSetting("autoScrape", "Fetch artwork for new games", null, s.scraper.autoScrapeNew) { v -> set { it.copy(scraper = it.scraper.copy(autoScrapeNew = v)) } },
            ActionSetting("scrapeMissing", "Fetch missing artwork", null, "Fetch", { app.scraper.scrape(app.library.value) }),
        )
        SettingsSection.BIOS -> listOf(
            PanelSetting("bios", "BIOS files", "Only systems in your library are listed", s.biosDir ?: AppPaths.system.path) { BiosPanel() },
        )
        SettingsSection.ABOUT -> listOf(
            InfoSetting("version", "Material Frontend", "Version 0.1 (work in progress) · by XDan"),
            ActionSetting("site", "xdan.cc", null, "Open", { Launchers.openUri("https://xdan.cc") }),
            ActionSetting("setupAgain", "Run setup again", "Everything else stays as it is", "Start", { app.runSetupAgain() }),
            ActionSetting("resetSettings", "Reset settings", "Default settings; your library is kept", "Reset", { app.resetSettings() }, true,
                "All settings — folders, emulators, keys, appearance — go back to their defaults."),
            ActionSetting("clearLibrary", "Clear library", "Files on disk aren't touched; a rescan brings games back", "Clear", { app.clearLibrary() }, true,
                "Favourites, play time and edited titles are stored in the library and will be lost."),
            ActionSetting("resetAll", "Reset everything", "Settings, library, downloaded artwork, core options", "Reset all", { app.resetEverything() }, true,
                "This also deletes downloaded artwork and saved core options. Saves, save states, installed cores, BIOS files and your games are kept."),
        )
    }
}
