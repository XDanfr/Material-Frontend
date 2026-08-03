package app.materialfrontend.emulators

/**
 * Standalone emulators the frontend knows how to find and launch.
 * Arg templates support {rom} {romdir} {romname} {core} tokens; every template is user-editable.
 */
data class EmulatorDef(
    val id: String,
    val name: String,
    val platforms: List<String>,
    /** Executable base names (without .exe) / AppImage prefixes, case-insensitive. */
    val binaries: List<String>,
    /** macOS .app bundle names (without .app). */
    val macApps: List<String> = emptyList(),
    val flatpak: List<String> = emptyList(),
    val args: String = "\"{rom}\"",
    val website: String = "",
    val note: String? = null,
)

object EmulatorCatalog {
    private val switchArgs = "-f -g \"{rom}\""

    val all: List<EmulatorDef> = listOf(
        EmulatorDef("retroarch", "RetroArch", listOf("*"), listOf("retroarch"), listOf("RetroArch"), listOf("org.libretro.RetroArch"),
            "-f -L \"{core}\" \"{rom}\"", "https://www.retroarch.com", "Used as a fallback for hardware-rendered cores."),
        EmulatorDef("duckstation", "DuckStation", listOf("psx"), listOf("duckstation-qt-x64-releaseltcg", "duckstation-qt", "duckstation"), listOf("DuckStation"), listOf("org.duckstation.DuckStation"),
            "-batch -fullscreen -- \"{rom}\""),
        EmulatorDef("pcsx2", "PCSX2", listOf("ps2"), listOf("pcsx2-qt", "pcsx2-qtx64-avx2", "pcsx2-qtx64", "pcsx2"), listOf("PCSX2"), listOf("net.pcsx2.PCSX2"),
            "-batch -nogui -fullscreen -- \"{rom}\""),
        EmulatorDef("rpcs3", "RPCS3", listOf("ps3"), listOf("rpcs3"), listOf("RPCS3"), listOf("net.rpcs3.RPCS3"), "--no-gui \"{rom}\""),
        EmulatorDef("shadps4", "shadPS4", listOf("ps4"), listOf("shadps4", "shadps4-qt"), listOf("shadps4", "shadPS4"), listOf("net.shadps4.shadPS4"),
            "-g \"{rom}\" -f true"),
        EmulatorDef("vita3k", "Vita3K", listOf("psvita"), listOf("vita3k"), listOf("Vita3K"), listOf("org.vita3k.Vita3K"), "-F \"{rom}\""),
        EmulatorDef("ppsspp", "PPSSPP", listOf("psp"), listOf("ppssppwindows64", "ppssppwindows", "ppssppsdl", "ppssppqt", "ppsspp"), listOf("PPSSPPSDL", "PPSSPP"), listOf("org.ppsspp.PPSSPP"),
            "--fullscreen \"{rom}\""),
        EmulatorDef("dolphin", "Dolphin", listOf("gc", "wii"), listOf("dolphin", "dolphin-emu"), listOf("Dolphin"), listOf("org.DolphinEmu.dolphin-emu"), "-b -e \"{rom}\""),
        EmulatorDef("cemu", "Cemu", listOf("wiiu"), listOf("cemu"), listOf("Cemu"), listOf("info.cemu.Cemu"), "-f -g \"{rom}\""),
        EmulatorDef("ryujinx", "Ryujinx (Ryubing)", listOf("switch"), listOf("ryujinx"), listOf("Ryujinx"), listOf("io.github.ryubing.Ryujinx", "org.ryujinx.Ryujinx"),
            "--fullscreen \"{rom}\""),
        EmulatorDef("citron", "Citron", listOf("switch"), listOf("citron"), listOf("citron", "Citron"), listOf("org.citron_emu.citron"), switchArgs),
        EmulatorDef("eden", "Eden", listOf("switch"), listOf("eden"), listOf("Eden", "eden"), listOf("dev.eden_emu.eden"), switchArgs),
        EmulatorDef("sudachi", "Sudachi", listOf("switch"), listOf("sudachi"), listOf("sudachi", "Sudachi"), emptyList(), switchArgs),
        EmulatorDef("suyu", "suyu", listOf("switch"), listOf("suyu"), listOf("suyu"), emptyList(), switchArgs),
        EmulatorDef("torzu", "Torzu", listOf("switch"), listOf("torzu", "yuzu-torzu"), listOf("torzu"), emptyList(), switchArgs),
        EmulatorDef("yuzu", "yuzu", listOf("switch"), listOf("yuzu"), listOf("yuzu"), listOf("org.yuzu_emu.yuzu"), switchArgs),
        EmulatorDef("eglantine", "Eglantine", listOf("switch2"), listOf("eglantine"), listOf("Eglantine", "eglantine"), emptyList(), "\"{rom}\"",
            note = "Switch 2 emulator — command-line flags may change; edit the arguments if needed."),
        EmulatorDef("azahar", "Azahar", listOf("3ds"), listOf("azahar", "azahar-qt"), listOf("Azahar"), listOf("org.azahar_emu.Azahar"), "-f \"{rom}\""),
        EmulatorDef("lime3ds", "Lime3DS", listOf("3ds"), listOf("lime3ds", "lime3ds-gui", "lime-qt"), listOf("Lime3DS"), listOf("io.github.lime3ds.Lime3DS"), "-f \"{rom}\""),
        EmulatorDef("citra", "Citra", listOf("3ds"), listOf("citra-qt", "citra"), listOf("citra-qt", "Citra"), listOf("org.citra_emu.citra"), "-f \"{rom}\""),
        EmulatorDef("melonds", "melonDS", listOf("nds"), listOf("melonds"), listOf("melonDS"), listOf("net.kuribo64.melonDS"), "-f \"{rom}\""),
        EmulatorDef("xemu", "xemu", listOf("xbox"), listOf("xemu"), listOf("xemu"), listOf("app.xemu.xemu"), "-full-screen -dvd_path \"{rom}\""),
        EmulatorDef("xenia_canary", "Xenia Canary", listOf("xbox360"), listOf("xenia_canary", "xenia-canary", "xenia_canary_netplay"), emptyList(), emptyList(),
            "\"{rom}\" --fullscreen=true", note = "Windows build, or the Linux AppImage (e.g. the one shipped for Decky/EmuDeck)."),
        EmulatorDef("xenia", "Xenia", listOf("xbox360"), listOf("xenia"), emptyList(), emptyList(), "\"{rom}\" --fullscreen=true"),
        EmulatorDef("flycast", "Flycast", listOf("dreamcast"), listOf("flycast"), listOf("Flycast"), listOf("org.flycast.Flycast"), "\"{rom}\""),
        EmulatorDef("redream", "Redream", listOf("dreamcast"), listOf("redream"), listOf("Redream"), emptyList(), "\"{rom}\""),
        EmulatorDef("mgba", "mGBA", listOf("gb", "gbc", "gba"), listOf("mgba", "mgba-qt"), listOf("mGBA"), listOf("io.mgba.mGBA"), "-f \"{rom}\""),
        EmulatorDef("mesen", "Mesen", listOf("nes", "snes", "gb", "gbc", "gba", "pcengine", "mastersystem", "gamegear"), listOf("mesen"), listOf("Mesen"), emptyList(), "\"{rom}\" --fullscreen"),
        EmulatorDef("ares", "ares", listOf("nes", "snes", "n64", "megadrive", "pcengine", "gba", "mastersystem", "gamegear", "sega32x", "segacd", "psx", "ngp", "wonderswan"),
            listOf("ares"), listOf("ares"), listOf("dev.ares.ares"), "--fullscreen \"{rom}\""),
        EmulatorDef("rmg", "Rosalie's Mupen GUI", listOf("n64"), listOf("rmg"), listOf("RMG"), listOf("com.github.Rosalie241.RMG"), "-f -n \"{rom}\""),
        EmulatorDef("simple64", "simple64", listOf("n64"), listOf("simple64-gui", "simple64"), emptyList(), listOf("io.github.simple64.simple64"), "--nogui \"{rom}\""),
        EmulatorDef("mame", "MAME", listOf("arcade", "neogeo"), listOf("mame", "mame64"), listOf("MAME", "mame"), listOf("org.mamedev.MAME"), "-rompath \"{romdir}\" {romname}"),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): EmulatorDef? = byId[id]
}
