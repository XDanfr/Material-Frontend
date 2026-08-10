package app.materialfrontend.model

/**
 * A game system. `extensions` that also appear on other platforms (iso, chd, cue, zip…) are only
 * used when the folder name identifies the platform; unique extensions identify it anywhere.
 */
data class Platform(
    val id: String,
    val name: String,
    val shortName: String,
    val manufacturer: String,
    val year: Int,
    val extensions: Set<String>,
    val aliases: Set<String>,
    /** libretro-thumbnails repository name. */
    val thumbnailsName: String? = null,
    val screenScraperId: Int? = null,
    val igdbId: Int? = null,
    /** Libretro core ids in order of preference. */
    val cores: List<String> = emptyList(),
    /** Standalone emulator ids (EmulatorCatalog) in order of preference. */
    val emulators: List<String> = emptyList(),
    /** Games are directories (PS3/PS4/Wii U) recognised by these marker paths. */
    val dirMarkers: List<String> = emptyList(),
)

object Platforms {
    const val STEAM = "steam"
    const val PC = "pc"

    private val disc = setOf("cue", "chd", "iso", "m3u")

    val all: List<Platform> = listOf(
        // Nintendo
        Platform("nes", "Nintendo Entertainment System", "NES", "Nintendo", 1983, setOf("nes", "fds", "unf", "unif", "zip", "7z"),
            setOf("nes", "famicom", "fc", "fds"), "Nintendo - Nintendo Entertainment System", 3, 18,
            listOf("mesen", "nestopia", "fceumm"), listOf("mesen", "ares", "retroarch")),
        Platform("snes", "Super Nintendo", "SNES", "Nintendo", 1990, setOf("sfc", "smc", "swc", "fig", "bs", "zip", "7z"),
            setOf("snes", "sfc", "superfamicom", "superfami"), "Nintendo - Super Nintendo Entertainment System", 4, 19,
            listOf("snes9x", "bsnes", "mesen-s"), listOf("ares", "retroarch")),
        Platform("n64", "Nintendo 64", "N64", "Nintendo", 1996, setOf("n64", "z64", "v64", "zip", "7z"),
            setOf("n64", "nintendo64"), "Nintendo - Nintendo 64", 14, 4,
            listOf("mupen64plus_next", "parallel_n64"), listOf("rmg", "simple64", "ares", "retroarch")),
        Platform("gb", "Game Boy", "GB", "Nintendo", 1989, setOf("gb", "zip", "7z"),
            setOf("gb", "gameboy"), "Nintendo - Game Boy", 9, 33, listOf("gambatte", "sameboy", "mgba"), listOf("mgba", "retroarch")),
        Platform("gbc", "Game Boy Color", "GBC", "Nintendo", 1998, setOf("gbc", "zip", "7z"),
            setOf("gbc", "gameboycolor"), "Nintendo - Game Boy Color", 10, 22, listOf("gambatte", "sameboy", "mgba"), listOf("mgba", "retroarch")),
        Platform("gba", "Game Boy Advance", "GBA", "Nintendo", 2001, setOf("gba", "zip", "7z"),
            setOf("gba", "gameboyadvance"), "Nintendo - Game Boy Advance", 12, 24, listOf("mgba", "vba_next"), listOf("mgba", "retroarch")),
        Platform("nds", "Nintendo DS", "NDS", "Nintendo", 2004, setOf("nds", "dsi", "zip", "7z"),
            setOf("nds", "ds", "nintendods"), "Nintendo - Nintendo DS", 15, 20, listOf("melondsds", "desmume"), listOf("melonds", "retroarch")),
        Platform("3ds", "Nintendo 3DS", "3DS", "Nintendo", 2011, setOf("3ds", "cci", "cxi", "cia", "3dsx", "app"),
            setOf("3ds", "n3ds", "nintendo3ds"), "Nintendo - Nintendo 3DS", 17, 37, emptyList(), listOf("azahar", "lime3ds", "citra")),
        Platform("gc", "GameCube", "GC", "Nintendo", 2001, setOf("gcm", "gcz", "rvz", "ciso", "iso", "nkit.iso"),
            setOf("gc", "gamecube", "ngc"), "Nintendo - GameCube", 13, 21, emptyList(), listOf("dolphin")),
        Platform("wii", "Wii", "Wii", "Nintendo", 2006, setOf("wbfs", "wad", "rvz", "iso", "gcz", "wia"),
            setOf("wii"), "Nintendo - Wii", 16, 5, emptyList(), listOf("dolphin")),
        Platform("wiiu", "Wii U", "Wii U", "Nintendo", 2012, setOf("wua", "wud", "wux", "rpx"),
            setOf("wiiu"), "Nintendo - Wii U", 18, 41, emptyList(), listOf("cemu"), dirMarkers = listOf("code")),
        Platform("switch", "Nintendo Switch", "Switch", "Nintendo", 2017, setOf("nsp", "xci", "nca", "nro", "nsz", "xcz"),
            setOf("switch", "nsw", "nintendoswitch"), null, 225, 130, emptyList(),
            listOf("ryujinx", "citron", "eden", "sudachi", "suyu", "yuzu", "torzu")),
        Platform("switch2", "Nintendo Switch 2", "Switch 2", "Nintendo", 2025, setOf("nup", "nsp", "xci", "nsz", "xcz"),
            setOf("switch2", "ns2", "nintendoswitch2"), null, null, 508, emptyList(), listOf("eglantine")),

        // Sega
        Platform("mastersystem", "Master System", "SMS", "Sega", 1985, setOf("sms", "zip", "7z"),
            setOf("mastersystem", "sms", "segamastersystem", "mark3"), "Sega - Master System - Mark III", 2, 64,
            listOf("genesis_plus_gx", "picodrive"), listOf("retroarch")),
        Platform("megadrive", "Mega Drive / Genesis", "Genesis", "Sega", 1988, setOf("md", "gen", "smd", "bin", "zip", "7z"),
            setOf("megadrive", "genesis", "md", "segagenesis"), "Sega - Mega Drive - Genesis", 1, 29,
            listOf("genesis_plus_gx", "picodrive"), listOf("ares", "retroarch")),
        Platform("gamegear", "Game Gear", "GG", "Sega", 1990, setOf("gg", "zip", "7z"),
            setOf("gamegear", "gg"), "Sega - Game Gear", 21, 35, listOf("genesis_plus_gx"), listOf("retroarch")),
        Platform("sega32x", "Sega 32X", "32X", "Sega", 1994, setOf("32x", "zip", "7z"),
            setOf("sega32x", "32x"), "Sega - 32X", 19, 30, listOf("picodrive"), listOf("retroarch")),
        Platform("segacd", "Sega CD", "Sega CD", "Sega", 1991, disc + setOf("bin"),
            setOf("segacd", "megacd"), "Sega - Mega-CD - Sega CD", 20, 78, listOf("genesis_plus_gx", "picodrive"), listOf("retroarch")),
        Platform("saturn", "Sega Saturn", "Saturn", "Sega", 1994, disc + setOf("ccd", "mds"),
            setOf("saturn", "segasaturn"), "Sega - Saturn", 22, 32, listOf("mednafen_saturn", "yabasanshiro"), listOf("retroarch")),
        Platform("dreamcast", "Dreamcast", "DC", "Sega", 1998, setOf("cdi", "gdi") + disc,
            setOf("dreamcast", "dc"), "Sega - Dreamcast", 23, 23, listOf("flycast"), listOf("flycast", "redream", "retroarch")),

        // Sony
        Platform("psx", "PlayStation", "PS1", "Sony", 1994, disc + setOf("pbp", "ecm", "bin"),
            setOf("psx", "ps1", "playstation", "psone"), "Sony - PlayStation", 57, 7,
            listOf("pcsx_rearmed", "mednafen_psx", "swanstation", "mednafen_psx_hw"), listOf("duckstation", "retroarch")),
        Platform("ps2", "PlayStation 2", "PS2", "Sony", 2000, disc + setOf("cso", "gz", "elf"),
            setOf("ps2", "playstation2"), "Sony - PlayStation 2", 58, 8, emptyList(), listOf("pcsx2")),
        Platform("ps3", "PlayStation 3", "PS3", "Sony", 2006, setOf("pkg", "iso"),
            setOf("ps3", "playstation3"), "Sony - PlayStation 3", 59, 9, emptyList(), listOf("rpcs3"),
            dirMarkers = listOf("PS3_GAME/USRDIR/EBOOT.BIN", "USRDIR/EBOOT.BIN")),
        Platform("ps4", "PlayStation 4", "PS4", "Sony", 2013, setOf("pkg"),
            setOf("ps4", "playstation4"), null, 60, 48, emptyList(), listOf("shadps4"),
            dirMarkers = listOf("eboot.bin", "sce_sys/param.sfo")),
        Platform("ps5", "PlayStation 5", "PS5", "Sony", 2020, setOf("pkg"),
            setOf("ps5", "playstation5"), null, null, 167, emptyList(), emptyList(),
            dirMarkers = listOf("eboot.bin")),
        Platform("psp", "PlayStation Portable", "PSP", "Sony", 2004, setOf("cso", "pbp", "iso", "chd"),
            setOf("psp", "playstationportable"), "Sony - PlayStation Portable", 61, 38, listOf("ppsspp"), listOf("ppsspp", "retroarch")),
        Platform("psvita", "PlayStation Vita", "Vita", "Sony", 2011, setOf("vpk", "zip"),
            setOf("psvita", "vita"), "Sony - PlayStation Vita", 62, 46, emptyList(), listOf("vita3k")),

        // Microsoft
        Platform("xbox", "Xbox", "Xbox", "Microsoft", 2001, setOf("xiso", "iso"),
            setOf("xbox"), "Microsoft - Xbox", 32, 11, emptyList(), listOf("xemu")),
        Platform("xbox360", "Xbox 360", "X360", "Microsoft", 2005, setOf("xex", "iso", "zar", "god"),
            setOf("xbox360", "x360"), "Microsoft - Xbox 360", 33, 12, emptyList(), listOf("xenia_canary", "xenia")),

        // NEC / SNK / Atari / Bandai / misc
        Platform("pcengine", "PC Engine / TurboGrafx-16", "PCE", "NEC", 1987, setOf("pce", "sgx", "zip", "7z"),
            setOf("pcengine", "tg16", "turbografx16", "pce"), "NEC - PC Engine - TurboGrafx 16", 31, 86,
            listOf("mednafen_pce_fast", "mednafen_pce"), listOf("ares", "retroarch")),
        Platform("pcenginecd", "PC Engine CD", "PCE-CD", "NEC", 1988, disc,
            setOf("pcenginecd", "tg-cd", "turbografxcd", "pcecd"), "NEC - PC Engine CD - TurboGrafx-CD", 114, 150,
            listOf("mednafen_pce_fast"), listOf("retroarch")),
        Platform("arcade", "Arcade", "Arcade", "Various", 1978, setOf("zip", "7z"),
            setOf("arcade", "mame", "fbneo", "fba", "cps1", "cps2", "cps3"), "FBNeo - Arcade Games", 75, 52,
            listOf("fbneo", "mame2003_plus"), listOf("mame", "retroarch")),
        Platform("neogeo", "Neo Geo", "Neo Geo", "SNK", 1990, setOf("zip", "7z"),
            setOf("neogeo", "aes", "mvs"), "SNK - Neo Geo", 142, 80, listOf("fbneo"), listOf("retroarch")),
        Platform("ngp", "Neo Geo Pocket Color", "NGPC", "SNK", 1998, setOf("ngp", "ngc", "zip", "7z"),
            setOf("ngp", "ngpc", "neogeopocket"), "SNK - Neo Geo Pocket Color", 82, 120, listOf("mednafen_ngp"), listOf("retroarch")),
        Platform("atari2600", "Atari 2600", "2600", "Atari", 1977, setOf("a26", "zip", "7z"),
            setOf("atari2600", "2600"), "Atari - 2600", 26, 59, listOf("stella"), listOf("retroarch")),
        Platform("atari7800", "Atari 7800", "7800", "Atari", 1986, setOf("a78", "zip", "7z"),
            setOf("atari7800", "7800"), "Atari - 7800", 41, 60, listOf("prosystem"), listOf("retroarch")),
        Platform("lynx", "Atari Lynx", "Lynx", "Atari", 1989, setOf("lnx", "zip", "7z"),
            setOf("lynx", "atarilynx"), "Atari - Lynx", 28, 61, listOf("handy", "mednafen_lynx"), listOf("retroarch")),
        Platform("jaguar", "Atari Jaguar", "Jaguar", "Atari", 1993, setOf("j64", "jag", "zip"),
            setOf("jaguar", "atarijaguar"), "Atari - Jaguar", 27, 62, listOf("virtualjaguar"), listOf("retroarch")),
        Platform("wonderswan", "WonderSwan Color", "WSC", "Bandai", 1999, setOf("ws", "wsc", "zip", "7z"),
            setOf("wonderswan", "wonderswancolor", "wsc", "ws"), "Bandai - WonderSwan Color", 46, 123, listOf("mednafen_wswan"), listOf("retroarch")),
        Platform("3do", "3DO", "3DO", "Panasonic", 1993, disc,
            setOf("3do"), "The 3DO Company - 3DO", 29, 50, listOf("opera"), listOf("retroarch")),
        Platform("msx", "MSX", "MSX", "Microsoft", 1983, setOf("mx1", "mx2", "rom", "dsk", "zip"),
            setOf("msx", "msx2"), "Microsoft - MSX", 113, 27, listOf("bluemsx", "fmsx"), listOf("retroarch")),
        Platform("dos", "MS-DOS", "DOS", "IBM PC", 1981, setOf("dosz", "exe", "com", "bat", "zip"),
            setOf("dos", "msdos", "pc-dos"), "DOS", 135, 13, listOf("dosbox_pure"), listOf("retroarch")),

        // PC
        Platform(STEAM, "Steam", "Steam", "Valve", 2003, emptySet(), emptySet(), null, null, 6),
        Platform(PC, "PC", "PC", "PC", 1981, emptySet(), setOf("pc", "windows", "ports"), null, 135, 6),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): Platform = byId[id] ?: Platform(id, id, id, "?", 0, emptySet(), emptySet())
    fun find(id: String?): Platform? = id?.let { byId[it] }

    /** Normalised folder alias -> platform. */
    val aliasMap: Map<String, Platform> = buildMap {
        all.forEach { p -> p.aliases.forEach { put(normalize(it), p) }; put(normalize(p.id), p) }
    }

    /** Extensions that belong to exactly one platform. */
    val uniqueExtensions: Map<String, Platform> = all.flatMap { p -> p.extensions.map { it to p } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size == 1 }
        .mapValues { it.value.first() }

    fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Platforms a recompiled/decompiled PC port can be associated with. */
    val portTargets: List<Platform> get() = all.filter { it.id != STEAM }
}
