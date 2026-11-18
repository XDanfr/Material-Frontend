# Material Frontend (WIP)

By [XDan](https://xdan.cc).

A Material 3 Expressive emulation frontend for Windows, macOS and Linux, built with Compose Multiplatform.

- **Library view:** a Steam-library style grid or table with sorting, filtering, collections, multi-select and Ctrl+K quick launch.
- **Big Screen:** controller-first shelves, a game page, an on-screen-keyboard search and a quick menu.
- **XMB:** a cross-media-bar view with category morphs and tonal waves.
- **In-app play:** libretro cores run inside the window through the Java FFM API, with save states and thumbnails, rewind, fast-forward, a CRT/LCD filter, SRAM autosave and rumble.
  - **GPU cores (new):** cores that need OpenGL (N64, PSP, Dreamcast, PS1 HW) get an off-screen OpenGL context (CGL on macOS, GLFW on Windows/Linux) and their frames are read back into the same pipeline. RetroArch stays available under "Launch with". GLES/Vulkan-only cores aren't supported yet.
  - **Core options:** legacy, v1 and v2 (with categories) appear in the in-game menu and are saved per core.
  - **Disc swapping:** multi-disc games can change disc from the in-game menu.
  - **Controls and players:** remap RetroPad buttons for one game or a whole system from the in-game menu (applies instantly), and choose which controller is which player.
- **Standalone emulators:** detected and launched with per-emulator argument templates. This includes RetroArch, DuckStation, PCSX2, RPCS3, shadPS4, Vita3K, PPSSPP, Dolphin, Cemu, Ryujinx/Ryubing, Citron, Eden, Sudachi, suyu, Torzu, yuzu, Eglantine (Switch 2), Azahar, Lime3DS, melonDS, xemu, Xenia/Xenia Canary (including the Linux AppImage), Flycast, Redream, mGBA, Mesen, ares, RMG, simple64 and MAME. You can also add your own emulator, for example for PS5 when one exists.
- **Steam import:** reads `libraryfolders.vdf`, the app manifests and non-Steam shortcuts (`shortcuts.vdf`), and uses the local library art and custom grid art.
- **Other launchers:**
  - Epic Games Launcher (`.item` manifests; the rebuilt launcher, including its Linux preview, is searched best-effort).
  - EA app (`__Installer/installerdata.xml`).
  - Xbox app (GDK packages with a `MicrosoftGame.config`, launched via `shell:AppsFolder`).
  - GOG Galaxy and Ubisoft Connect (registry).
  - Heroic (Epic, GOG and Amazon).
  - Lutris.
  - Xbox games on Linux through Xodus/Nexus show up via Nexus's "Add to Steam" shortcuts.
- **Existing setups:** ES-DE / EmulationStation `gamelist.xml` (including ES-DE's `downloaded_media`) and Pegasus `metadata.pegasus.txt` metadata and art are imported. They only fill gaps, so your own edits are kept.
- **Ports, recomps and decomps:** pick the executable. The game and its original system are guessed (Zelda64Recomp → Majora's Mask on N64) and you can edit both.
- **Scraping:**
  - SteamGridDB (API key; prefers exact title matches) for covers, heroes and logos
  - IGDB (Twitch credentials) for metadata
  - libretro-thumbnails (no key) for retro box art
  - Steam store and CDN for Steam games
- **Controllers:** SDL3 through LWJGL, with hot-plug. It detects Xbox, PlayStation, Nintendo, Steam and 8BitDo pads, shows matching button glyphs, has an option for Nintendo-style confirm, and maps pads to 4 players.
- **Setup:** one question per screen, centred cards, the same Back/Continue buttons throughout; controller, keyboard or mouse. It continues from the launch animation, asks what you'll use the app for (PC games, emulation or both) and only asks what's relevant: theme (Library, Big Screen or XMB, with previews), light or dark, PC libraries, game folders, emulators, cores, BIOS (only for your systems), artwork. Progress is saved, so it resumes where you left off, including after switching to fullscreen.
- **Settings in every mode:** one settings list, rendered as the Settings screen (Library view), a Settings tab (Big Screen) and the Settings category (XMB); a change anywhere shows everywhere. Each mode also has its own section (layout and sort; clock and hints; waves and hints). Accent colours are Pixel-style chips showing each seed's primary, secondary and tertiary colours.
- **Steam in CrossOver and Parallels:** Windows Steam games in CrossOver bottles (macOS and Linux) and in Parallels VMs with shared drives are listed and launched through CrossOver's `wine` / `prlctl`.
- **Start-up rescan:** new games are picked up every launch (can be turned off).

## Build and run

The only requirement is any JDK (11 or newer) to start the Gradle launcher. The build downloads JDK 25 itself (Gradle toolchains plus the daemon JVM criteria in `gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew :composeApp:run                         # run
./gradlew :composeApp:run --args="--bigscreen"    # start in Big Screen (also --xmb)
./gradlew :composeApp:jvmTest                     # unit tests
./gradlew :composeApp:packageDistributionForCurrentOS   # dmg / msi+exe / deb+rpm+AppImage dir
```

CI (`.github/workflows/build.yml`) tests and packages on Windows x64, Linux x64 and arm64, macOS Apple Silicon and macOS Intel. Tagging `v*` creates a draft release containing every installer. The macOS builds aren't signed, so right-click → Open the first time.

### Optional tests

| Env var | Effect |
|---|---|
| `MF_CORE_TESTS=1` | Downloads the tiny `2048` core and checks frames come out of the FFM libretro host |
| `MF_SCREENSHOTS=<dir>` | Renders every UI mode offscreen to PNGs |
| `MF_DATA_DIR=<dir>` | Uses a separate data folder (portable installs, tests) |

## Controls

| | Controller | Keyboard |
|---|---|---|
| Navigate | D-pad / left stick | Arrows |
| Confirm / Back | A / B (or ✕ / ○, swappable for Nintendo) | Enter / Esc |
| Favourite · Search | X · Y | F · / |
| Tabs · Pages | LB/RB · LT/RT | Q/E · PgUp/PgDn |
| Menu | Start | Tab |
| In-game menu | Guide, or Select + Start | Esc / F1 |
| Fast-forward | Select + RB (toggle) | hold Tab |
| Rewind | hold Select + LB | hold Backspace |
| Save / load state | Quick menu | F5 / F7 |
| Close external emulator | Guide | — |

In game, the keyboard maps to the RetroPad as follows: arrows = D-pad, Z/X/A/S = face buttons, Q/W = L/R, 1/3 = L2/R2, Enter = Start and Shift = Select. Fullscreen toggles with F11 on Windows and Linux and ⌃⌘F (Control-Command-F) on macOS; the green window button and Globe-F stay in sync.

## Icons and type

- **App icon:** `packaging/macos/AppIcon.icon` is the Icon Composer source.
  - `packaging/macos/build-icon.sh` (Xcode 26+) compiles it to `Assets.car`, which gives the Liquid Glass icon, plus `AppIcon.icns`. Both outputs are committed, so CI doesn't need Xcode 26.
  - The Windows `.ico` and Linux `.png` are rendered from `composeApp/src/jvmMain/resources/icons/app-icon.svg` with `MF_WRITE_ICONS=$PWD ./gradlew :composeApp:jvmTest --tests '*IconAssetsTest*'`.
- **In-app icons** (`ui/brand`): the seven icons in `packaging/icons/transparent` are rebuilt as live Material shapes, so they can morph into each other while their backdrops spin. There are two loaders:
  - `BrandLoader` cycles the whole set and is used for long loads.
  - `CartridgeLoader` keeps the cartridge still while its label shape runs the M3 loading-indicator morphs, for quick loads.
- **Type:** Google Sans Flex (SIL OFL), a variable font. Interactions animate its weight, width, roundness and slant (`ui/theme/Flex.kt`).

## Layout

```
composeApp/src/jvmMain/kotlin/cc/xdan/frontend/
  model/        Game, Settings, Platform catalogue (40+ systems)
  data/         JSON stores, ROM scanner, hashing, Steam (VDF), port guessing
  emulators/    Emulator catalogue + detector, cores + installer, BIOS check, launcher
  libretro/     FFM bindings (LibretroCore) and the emulation session (video/audio/input/states)
  input/        SDL3 controller manager, nav events, keyboard bridge
  scraper/      Providers + scrape queue + asset cache
  ui/           theme, components, library, bigscreen, xmb, emulation, onboarding, settings
```

Data lives in `~/Library/Application Support/MaterialFrontend`, `%APPDATA%\MaterialFrontend` or `~/.local/share/material-frontend`. That covers settings, the library, cores, saves, states and media.

## Status and roadmap

**Works now:**
- All three UI modes.
- Scanning, scraping and Steam import.
- External launching and play-time tracking.
- Software-rendered libretro cores: NES, SNES, GB/GBC/GBA, NDS (melonDS DS), Genesis/SMS/GG/32X/Sega CD, Saturn, PS1 (PCSX ReARMed / Beetle), PCE, arcade (FBNeo), Atari, WonderSwan, NGP, 3DO, MSX and DOS.

**Next:**
- Vulkan / GLES hardware contexts for cores that don't offer OpenGL.
- RetroAchievements.
- Battle.net, itch.io and Amazon Games app (Windows) sources.
- A direct Xodus integration, once it publishes an interface for other apps.

BIOS files, firmware and keys are never downloaded; you supply your own dumps.
