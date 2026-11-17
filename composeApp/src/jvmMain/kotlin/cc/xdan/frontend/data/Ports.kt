package cc.xdan.frontend.data

import java.io.File

/**
 * Recompilations / decompilations / source ports. Guesses the original game and system from the
 * executable or folder name; the user can always edit both.
 */
object Ports {
    data class Known(val pattern: Regex, val title: String, val platformId: String, val kind: String)

    private fun k(p: String, title: String, platform: String, kind: String = "Recomp") = Known(Regex(p, RegexOption.IGNORE_CASE), title, platform, kind)

    val known = listOf(
        k("zelda64|mmrecomp|majora", "The Legend of Zelda: Majora's Mask", "n64"),
        k("2ship", "The Legend of Zelda: Majora's Mask", "n64", "Port"),
        k("^soh|shipofharkinian|ship.of.harkinian", "The Legend of Zelda: Ocarina of Time", "n64", "Port"),
        k("sm64|render96|ghostship", "Super Mario 64", "n64", "Port"),
        k("starship", "Star Fox 64", "n64", "Port"),
        k("spaghetti", "Mario Kart 64", "n64", "Port"),
        k("perfect.?dark|^pd\\.(x86|arm|exe)", "Perfect Dark", "n64", "Port"),
        k("banjo", "Banjo-Kazooie", "n64"),
        k("goemon", "Mystical Ninja Starring Goemon", "n64"),
        k("dinosaur.?planet|dinorecomp", "Dinosaur Planet", "n64"),
        k("unleashed", "Sonic Unleashed", "xbox360"),
        k("marathon", "Sonic the Hedgehog (2006)", "xbox360"),
        k("opengoal|^gk(\\.exe)?$", "Jak and Daxter: The Precursor Legacy", "ps2", "Port"),
        k("^re3", "Grand Theft Auto III", "ps2", "Port"),
        k("^revc", "Grand Theft Auto: Vice City", "ps2", "Port"),
        k("dusk", "The Legend of Zelda: Twilight Princess", "gc", "Port"),
        k("sonic3air|sonic3 a\\.?i\\.?r", "Sonic 3 & Knuckles", "megadrive", "Port"),
        k("openlara", "Tomb Raider", "psx", "Port"),
        k("wipeout|wipegame", "Wipeout", "psx", "Port"),
        k("sotn|castlevania", "Castlevania: Symphony of the Night", "psx", "Port"),
        k("smw|supermarioworld", "Super Mario World", "snes", "Port"),
        k("zelda3", "The Legend of Zelda: A Link to the Past", "snes", "Port"),
        k("sm(_|-)?port|super.?metroid", "Super Metroid", "snes", "Port"),
    )

    data class Guess(val title: String, val platformId: String?, val kind: String)

    fun guess(executable: File): Guess {
        val candidates = listOf(executable.nameWithoutExtension, executable.parentFile?.name.orEmpty())
        for (c in candidates) known.firstOrNull { it.pattern.containsMatchIn(c) }?.let { return Guess(it.title, it.platformId, it.kind) }
        return Guess(prettify(executable.nameWithoutExtension), null, "Port")
    }

    /** "Zelda64Recompiled-v1.2-x86_64" -> "Zelda64" ; camelCase split, junk removed. */
    fun prettify(stem: String): String = stem
        .replace(Regex("(?i)[-_ .]?(v?\\d+(\\.\\d+)+)"), "")
        .replace(Regex("(?i)(x86_64|x64|amd64|arm64|aarch64|win64|linux|macos|appimage|portable|release|debug)"), "")
        .replace(Regex("(?i)(recompiled|recomp|decomp|port)"), "")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("[-_.]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { stem }
}
