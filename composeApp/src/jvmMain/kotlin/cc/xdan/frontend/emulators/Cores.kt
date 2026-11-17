package cc.xdan.frontend.emulators

import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** A libretro core. `hw` = needs RETRO_ENVIRONMENT_SET_HW_RENDER (runs via RetroArch until in-app GL lands). */
data class CoreDef(val id: String, val name: String, val platforms: List<String>, val hw: Boolean = false, val sizeMb: Int = 3)

object CoreCatalog {
    val all = listOf(
        CoreDef("mesen", "Mesen", listOf("nes"), sizeMb = 4),
        CoreDef("nestopia", "Nestopia UE", listOf("nes")),
        CoreDef("fceumm", "FCEUmm", listOf("nes")),
        CoreDef("snes9x", "Snes9x", listOf("snes"), sizeMb = 3),
        CoreDef("bsnes", "bsnes", listOf("snes"), sizeMb = 6),
        CoreDef("mesen-s", "Mesen-S", listOf("snes")),
        CoreDef("mupen64plus_next", "Mupen64Plus-Next", listOf("n64"), hw = true, sizeMb = 9),
        CoreDef("parallel_n64", "ParaLLEl N64", listOf("n64"), hw = true, sizeMb = 9),
        CoreDef("gambatte", "Gambatte", listOf("gb", "gbc"), sizeMb = 1),
        CoreDef("sameboy", "SameBoy", listOf("gb", "gbc"), sizeMb = 2),
        CoreDef("mgba", "mGBA", listOf("gba", "gb", "gbc"), sizeMb = 3),
        CoreDef("vba_next", "VBA Next", listOf("gba")),
        CoreDef("melondsds", "melonDS DS", listOf("nds"), sizeMb = 8),
        CoreDef("desmume", "DeSmuME", listOf("nds"), sizeMb = 6),
        CoreDef("genesis_plus_gx", "Genesis Plus GX", listOf("megadrive", "mastersystem", "gamegear", "segacd"), sizeMb = 4),
        CoreDef("picodrive", "PicoDrive", listOf("megadrive", "sega32x", "segacd", "mastersystem")),
        CoreDef("mednafen_saturn", "Beetle Saturn", listOf("saturn"), sizeMb = 6),
        CoreDef("yabasanshiro", "YabaSanshiro", listOf("saturn"), hw = true),
        CoreDef("flycast", "Flycast", listOf("dreamcast", "arcade"), hw = true, sizeMb = 12),
        CoreDef("pcsx_rearmed", "PCSX ReARMed", listOf("psx"), sizeMb = 2),
        CoreDef("mednafen_psx", "Beetle PSX", listOf("psx"), sizeMb = 5),
        CoreDef("swanstation", "SwanStation", listOf("psx"), sizeMb = 4),
        CoreDef("mednafen_psx_hw", "Beetle PSX HW", listOf("psx"), hw = true, sizeMb = 6),
        CoreDef("ppsspp", "PPSSPP", listOf("psp"), hw = true, sizeMb = 15),
        CoreDef("mednafen_pce_fast", "Beetle PCE Fast", listOf("pcengine", "pcenginecd")),
        CoreDef("mednafen_pce", "Beetle PCE", listOf("pcengine", "pcenginecd")),
        CoreDef("fbneo", "FinalBurn Neo", listOf("arcade", "neogeo"), sizeMb = 30),
        CoreDef("mame2003_plus", "MAME 2003-Plus", listOf("arcade"), sizeMb = 25),
        CoreDef("mednafen_ngp", "Beetle NeoPop", listOf("ngp")),
        CoreDef("stella", "Stella", listOf("atari2600")),
        CoreDef("prosystem", "ProSystem", listOf("atari7800")),
        CoreDef("handy", "Handy", listOf("lynx")),
        CoreDef("mednafen_lynx", "Beetle Lynx", listOf("lynx")),
        CoreDef("virtualjaguar", "Virtual Jaguar", listOf("jaguar")),
        CoreDef("mednafen_wswan", "Beetle WonderSwan", listOf("wonderswan")),
        CoreDef("opera", "Opera", listOf("3do")),
        CoreDef("bluemsx", "blueMSX", listOf("msx")),
        CoreDef("fmsx", "fMSX", listOf("msx")),
        CoreDef("dosbox_pure", "DOSBox Pure", listOf("dos"), sizeMb = 8),
        CoreDef("2048", "2048 (test core)", emptyList()),
    )
    private val byId = all.associateBy { it.id }
    fun get(id: String) = byId[id]
    fun forPlatform(platformId: String) = all.filter { platformId in it.platforms }

    val libExtension = when (HostOs.current) { HostOs.WINDOWS -> "dll"; HostOs.MAC -> "dylib"; HostOs.LINUX -> "so" }

    fun fileName(id: String) = "${id}_libretro.$libExtension"

    /** Nightly buildbot URL for this OS/arch, or null if the buildbot has no build for it. */
    fun downloadUrl(id: String): String? {
        val dir = when (HostOs.current) {
            HostOs.WINDOWS -> if (HostOs.arch == "x86_64") "windows/x86_64" else return null
            HostOs.MAC -> "apple/osx/${HostOs.arch}"
            HostOs.LINUX -> if (HostOs.arch == "x86_64") "linux/x86_64" else return null
        }
        return "https://buildbot.libretro.com/nightly/$dir/latest/${fileName(id)}.zip"
    }

    fun installedFile(id: String): File? = File(AppPaths.cores, fileName(id)).takeIf { it.isFile }
    fun isInstalled(id: String) = installedFile(id) != null
}

class CoreInstaller(private val http: HttpClient) {
    /** Downloads and unzips a core. Reports progress 0..1. */
    suspend fun install(id: String, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = CoreCatalog.downloadUrl(id) ?: error("No buildbot build of $id for this OS/architecture")
            val tmp = File.createTempFile("core-$id", ".zip", AppPaths.cache)
            try {
                http.prepareGet(url).execute { resp ->
                    if (!resp.status.isSuccess()) error("HTTP ${resp.status.value} for $url")
                    val total = resp.contentLength() ?: -1L
                    val ch = resp.bodyAsChannel()
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    tmp.outputStream().use { out ->
                        while (!ch.isClosedForRead) {
                            val n = ch.readAvailable(buf)
                            if (n <= 0) continue
                            out.write(buf, 0, n); read += n
                            if (total > 0) onProgress(read.toFloat() / total)
                        }
                    }
                }
                val target = File(AppPaths.cores, CoreCatalog.fileName(id))
                ZipInputStream(tmp.inputStream()).use { zip ->
                    var e = zip.nextEntry
                    var wrote = false
                    while (e != null) {
                        if (!e.isDirectory && e.name.endsWith(".${CoreCatalog.libExtension}")) {
                            val part = File(target.path + ".part")
                            part.outputStream().use { zip.copyTo(it) }
                            part.renameTo(target.also { it.delete() }); wrote = true
                        }
                        e = zip.nextEntry
                    }
                    if (!wrote) error("Archive did not contain a core library")
                }
                onProgress(1f)
                target
            } finally { tmp.delete() }
        }
    }

    fun uninstall(id: String) { CoreCatalog.installedFile(id)?.delete() }
}

/** BIOS files checked (never downloaded). MD5 = known-good dump; mismatches are only a warning. */
data class BiosFile(val platformId: String, val file: String, val md5: String?, val required: Boolean, val note: String = "")

object BiosCatalog {
    val all = listOf(
        BiosFile("psx", "scph5501.bin", "490f666e1afb15b7362b406ed1cea246", true, "USA (one region is enough)"),
        BiosFile("psx", "scph5500.bin", "8dd7d5296a650fac7319bce665a6a53c", false, "Japan"),
        BiosFile("psx", "scph5502.bin", "32736f17079d0b2b7024407c39bd3050", false, "Europe"),
        BiosFile("segacd", "bios_CD_U.bin", "2efd74e3232ff260e371b99f84024f7f", true, "USA"),
        BiosFile("saturn", "sega_101.bin", "85ec9ca47d8f6807718151cbcca8b964", false, "Japan"),
        BiosFile("saturn", "mpr-17933.bin", "3240872c70984b6cbfda1586cab68dbe", true, "USA/Europe"),
        BiosFile("pcenginecd", "syscard3.pce", "38179df8f4ac870017db21ebcbf53114", true),
        BiosFile("dreamcast", "dc/dc_boot.bin", "e10c53c2f8b90bab96ead2d368858623", true),
        BiosFile("dreamcast", "dc/dc_flash.bin", null, false),
        BiosFile("nds", "bios7.bin", "df692a80a5b1bc90728bc3dfc76cd948", false, "Optional with melonDS DS"),
        BiosFile("nds", "bios9.bin", "a392174eb3e572fed6447e956bde4b25", false),
        BiosFile("nds", "firmware.bin", null, false),
        BiosFile("gba", "gba_bios.bin", "a860e8c0b6d573d191e4ec7db1b1e4f6", false, "Optional"),
        BiosFile("nes", "disksys.rom", "ca30b50f880eb660a320674ed365ef7a", false, "Famicom Disk System"),
        BiosFile("lynx", "lynxboot.img", "fcd403db69f54290b51035d82f835e7b", true),
        BiosFile("3do", "panafz10.bin", "51f2f43ae2f3508a14d9f56597e2d3ce", true),
        BiosFile("neogeo", "neogeo.zip", null, true, "Place alongside ROMs or in system folder"),
    )

    enum class Status { OK, MISSING, UNKNOWN_VERSION }

    fun status(biosDir: File, b: BiosFile): Status {
        val f = File(biosDir, b.file)
        if (!f.isFile) return Status.MISSING
        if (b.md5 == null) return Status.OK
        return if (runCatching { Hashing.md5(f) }.getOrNull().equals(b.md5, ignoreCase = true)) Status.OK else Status.UNKNOWN_VERSION
    }
}
