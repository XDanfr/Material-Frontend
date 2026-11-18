package cc.xdan.frontend.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameKind {
    ROM, STEAM, PORT,
    /** Installed through another launcher (Epic, EA, Xbox, GOG, Ubisoft, Heroic, Lutris…); path = launch URI or executable. */
    LAUNCHER,
}

@Serializable
data class GameMeta(
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
    val players: String? = null,
    /** 0..1 */
    val rating: Float? = null,
    val region: String? = null,
)

/** Local file paths of downloaded/cached artwork. */
@Serializable
data class GameAssets(
    val cover: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val screenshot: String? = null,
    val icon: String? = null,
)

@Serializable
data class Game(
    val id: String,
    val title: String,
    val platformId: String,
    val kind: GameKind = GameKind.ROM,
    /** ROM path, Steam app id, or executable for ports. */
    val path: String,
    val fileSize: Long = 0,
    val crc32: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null,
    val playTimeSeconds: Long = 0,
    val playCount: Int = 0,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    /** "core:<coreId>" or "emu:<emulatorConfigId>"; null = platform default. */
    val launcherOverride: String? = null,
    /** Extra args for ports. */
    val launchArgs: String? = null,
    val meta: GameMeta = GameMeta(),
    val assets: GameAssets = GameAssets(),
    val scraped: Boolean = false,
    /** User edited title — don't overwrite on rescan/scrape. */
    val titleLocked: Boolean = false,
    /** RetroAchievements game id from the ROM hash (0 = not in RA's database; null = not looked up). */
    val raGameId: Int? = null,
    /** Your logo position for this game (wins over the global setting); null = follow the global setting. */
    val logoPin: LogoPin? = null,
    /** The game's own default (Steam's pinned position), used when the global setting is "Match Steam". */
    val defaultLogoPin: LogoPin? = null,
    /** Your logo size for this game (fraction of the card's width); null = default. */
    val logoWidth: Float? = null,
    /** Steam's logo width for this game (fraction), when known. */
    val defaultLogoWidth: Float? = null,
    /** False for owned-but-not-installed launcher games (shown greyed out with Install). */
    val installed: Boolean = true,
    /** Operating systems the game supports (Steam: windows/macos/linux), for installing. */
    val os: Set<String> = emptySet(),
) {
    val sortKey: String get() = title.lowercase().removePrefix("the ").trim()

    /** Steam app id for art/metadata: real Steam games, and Windows Steam in CrossOver/Parallels. */
    val steamAppId: String? get() = when {
        kind == GameKind.STEAM && !id.startsWith("steam-shortcut") -> path
        id.startsWith("crossover-steam-") || id.startsWith("parallels-steam-") -> id.substringAfterLast('-')
        else -> null
    }

    /** Per-game choice → global choice → the game's Steam default → bottom left. */
    fun effectiveLogoPin(global: LogoPin?): LogoPin = logoPin ?: global ?: defaultLogoPin ?: LogoPin.BOTTOM_LEFT
    fun effectiveLogoWidth(): Float = logoWidth ?: defaultLogoWidth ?: LogoPin.DEFAULT_WIDTH
}

@Serializable
enum class UiMode { LIST, BIGSCREEN, XMB }

/** What the user said they'll use the app for (shapes setup and defaults). */
@Serializable
enum class Usage { GAMES, EMULATION, BOTH }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

@Serializable
enum class SortOrder(val label: String) {
    TITLE("Title"), LAST_PLAYED("Last played"), PLAY_TIME("Play time"),
    ADDED("Recently added"), RELEASE("Release year"), RATING("Rating"), PLATFORM("Platform")
}

@Serializable
enum class LibraryView { GRID, LIST }

@Serializable
enum class VideoFilter(val label: String) { SHARP("Sharp pixels"), SMOOTH("Smooth"), SCANLINES("CRT scanlines"), LCD("LCD grid") }

@Serializable
data class RomFolder(val path: String, val platformOverride: String? = null)

/** A configured standalone emulator (detected or user-added). */
@Serializable
data class EmulatorConfig(
    val id: String,
    /** id in EmulatorCatalog, or "custom". */
    val defId: String,
    val name: String,
    val path: String,
    val args: String,
    val platforms: List<String>,
    val flatpakId: String? = null,
    val enabled: Boolean = true,
)

/** A system the user described for an emulator we don't know (scanned by its file extensions). */
@Serializable
data class CustomSystem(val id: String, val name: String, val extensions: List<String>)

@Serializable
data class ScraperSettings(
    val steamGridDbKey: String = "",
    /** Steam Web API key: complete owned-games list (otherwise only games Steam lists locally). */
    val steamApiKey: String = "",
    val igdbClientId: String = "",
    /** RetroAchievements username + Web API key (retroachievements.org → Settings → Keys). */
    val raUser: String = "",
    val raApiKey: String = "",
    val igdbClientSecret: String = "",
    val providerOrder: List<String> = listOf("steamgriddb", "igdb", "libretro"),
    val preferredRegion: String = "us",
    val downloadHeroes: Boolean = true,
    val autoScrapeNew: Boolean = true,
)

@Serializable
data class Settings(
    val onboardingComplete: Boolean = false,
    /** Setup step to resume at (step id), so progress survives restarts and layout changes. */
    val onboardingStep: String? = null,
    val usage: Usage? = null,
    val defaultMode: UiMode = UiMode.LIST,
    val bigScreenOnController: Boolean = true,
    val startFullscreen: Boolean = false,
    /** Pause the built-in player while another app is in front (controllers are ignored then anyway). */
    val pauseInBackground: Boolean = true,
    /** Wide hero art: sharp, or blurred (Steam's own blurred copy where there is one). */
    val blurredHeroes: Boolean = false,
    /** Logo position on wide art for every game without its own; null = each game's Steam default. */
    val logoPin: LogoPin? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Brand purple (M3 baseline primary). */
    val seedColor: Long = 0xFF6750A4,
    val colorFromArtwork: Boolean = true,
    val romFolders: List<RomFolder> = emptyList(),
    val biosDir: String? = null,
    val importSteam: Boolean = true,
    /** Epic, EA, Xbox, GOG Galaxy, Ubisoft, Heroic, Lutris. */
    val importLaunchers: Boolean = true,
    /** Launcher sources switched off (LauncherSources.Source ids). */
    val disabledLaunchers: Set<String> = emptySet(),
    /** Launchers (incl. "steam") the user has already been asked about; null = from before this existed. */
    val knownLaunchers: Set<String>? = null,
    /** Found emulators the user said no to ("emu:<path>"). */
    val dismissedDiscoveries: Set<String> = emptySet(),
    /** Games removed from the library (rescans skip them). */
    val removedGames: Set<String> = emptySet(),
    /** Show owned games that aren't installed (greyed out, with Install). */
    val showNotInstalled: Boolean = true,
    /** Frontend controls: Nav name → AWT key code / Pad name (added to the defaults). */
    val uiKeyBindings: Map<String, Long> = emptyMap(),
    val uiPadBindings: Map<String, String> = emptyMap(),
    /** Read existing ES-DE / EmulationStation gamelist.xml and Pegasus metadata files. */
    val importGamelists: Boolean = true,
    val emulators: List<EmulatorConfig> = emptyList(),
    val customSystems: List<CustomSystem> = emptyList(),
    /** platformId -> "core:<id>" | "emu:<configId>" */
    val defaultLauncher: Map<String, String> = emptyMap(),
    val scraper: ScraperSettings = ScraperSettings(),
    /** Nintendo-style confirm (east) / back (south). */
    val nintendoConfirm: Boolean = false,
    val sort: SortOrder = SortOrder.TITLE,
    val sortDescending: Boolean = false,
    val libraryView: LibraryView = LibraryView.GRID,
    val videoFilter: VideoFilter = VideoFilter.SHARP,
    val integerScale: Boolean = false,
    val rewind: Boolean = true,
    /** Run hardware-rendered (OpenGL) cores inside the app; otherwise they launch through RetroArch. */
    val gpuCoresInApp: Boolean = true,
    /** Button remaps: "system:<id>" / "game:<id>" → (RetroPad id → pad button name). */
    val inputMaps: Map<String, Map<String, String>> = emptyMap(),
    val autoSaveState: Boolean = true,
    val minimizeOnExternalLaunch: Boolean = true,
    val reduceMotion: Boolean = false,
    val launchAnimation: Boolean = true,
    val rescanOnStartup: Boolean = true,
    // Theme-specific
    val bigScreenClock: Boolean = true,
    val bigScreenHints: Boolean = true,
    val spotlight: SpotlightMode = SpotlightMode.RECENT,
    val spotlightGames: List<String> = emptyList(),
    /** Games kept out of the automatic spotlight modes. */
    val spotlightHidden: Set<String> = emptySet(),
    /** Name used in the Home greeting; blank → Steam's account name if found. */
    val userName: String = "",
    val xmbWaves: Boolean = true,
    val xmbBackground: XmbBackground = XmbBackground.WAVES,
    val xmbHints: Boolean = true,
)


/** XMB backdrops. */
@Serializable
/** What the Big Screen spotlight shows. */
enum class SpotlightMode(val label: String) { RECENT("Recently played"), FAVOURITES("Favourites"), RANDOM("Random"), CHOSEN("Chosen games") }

/** Where the logo sits on wide art: a 3×3 grid. Steam's four pinned positions map onto it (names kept for saved data). */
enum class LogoPin(val label: String, val steam: String?, val row: Int, val col: Int) {
    TOP_LEFT("Top left", null, 0, 0), UPPER_CENTER("Top centre", "UpperCenter", 0, 1), TOP_RIGHT("Top right", null, 0, 2),
    CENTER_LEFT("Centre left", null, 1, 0), CENTER("Centre", "CenterCenter", 1, 1), CENTER_RIGHT("Centre right", null, 1, 2),
    BOTTOM_LEFT("Bottom left", "BottomLeft", 2, 0), BOTTOM_CENTER("Bottom centre", "BottomCenter", 2, 1), BOTTOM_RIGHT("Bottom right", null, 2, 2);

    /** The neighbouring preset (D-pad), staying put at the edges. */
    fun moved(dRow: Int, dCol: Int): LogoPin = entries.first { it.row == (row + dRow).coerceIn(0, 2) && it.col == (col + dCol).coerceIn(0, 2) }

    companion object {
        fun fromSteam(s: String?) = entries.firstOrNull { it.steam.equals(s, true) }
        /** Steam's positions (what the global setting offers). */
        val steamPins = entries.filter { it.steam != null }
        const val DEFAULT_WIDTH = 0.4f
    }
}

enum class XmbBackground(val label: String) { WAVES("Waves"), SHAPES("Shapes"), SQUIGGLES("Squiggles"), MIX("Mix"), OFF("Off") }
