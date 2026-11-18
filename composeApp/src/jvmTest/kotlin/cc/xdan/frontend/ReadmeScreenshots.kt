package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import cc.xdan.frontend.input.Nav
import cc.xdan.frontend.input.Pad
import cc.xdan.frontend.model.EmulatorConfig
import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.ThemeMode
import cc.xdan.frontend.model.UiMode
import cc.xdan.frontend.model.XmbBackground
import cc.xdan.frontend.ui.App
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the README screenshots from a demo library (never your own).
 * MF_README_SHOTS=<out dir> MF_DATA_DIR=<scratch dir> [MF_SGDB_KEY=<SteamGridDB key>] ./gradlew :composeApp:jvmTest --tests '*ReadmeScreenshots*'
 */
class ReadmeScreenshots {
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    private fun steam(appId: String, title: String, played: Long? = null, fav: Boolean = false, hours: Long = 0) =
        Game("steam-$appId", title, "steam", kind = GameKind.STEAM, path = appId, lastPlayed = played?.let { now - it }, favorite = fav,
            playTimeSeconds = hours * 3600, addedAt = now - 30 * day)

    private fun rom(platform: String, title: String, played: Long? = null, fav: Boolean = false) =
        Game("$platform-${title.hashCode()}", title, platform, path = "/demo/$platform/$title.rom", lastPlayed = played?.let { now - it }, favorite = fav,
            addedAt = now - 60 * day)

    @Test fun render() {
        val out = System.getenv("MF_README_SHOTS")?.let(::File)?.also { it.mkdirs() } ?: return
        System.getenv("MF_DATA_DIR") ?: return
        val app = AppController()
        app.updateSettings { s ->
            s.copy(onboardingComplete = true, userName = "Player", themeMode = ThemeMode.DARK, launchAnimation = false,
                xmbBackground = XmbBackground.MIX, xmbWaves = true,
                scraper = s.scraper.copy(steamGridDbKey = System.getenv("MF_SGDB_KEY") ?: s.scraper.steamGridDbKey),
                // A stand-in emulator so the retro systems count as playable in the demo.
                emulators = listOf(EmulatorConfig("demo", "custom", "Demo", "/demo/emu", "{rom}", listOf("snes", "n64", "gba", "megadrive", "psx")),
                    EmulatorConfig("xenia", "xenia", "Xenia", "/demo/xenia", "\"{rom}\" --fullscreen=true", listOf("xbox360"))))
        }
        val games = listOf(
            rom("xbox360", "Blur", played = 3_600_000, fav = true),   // via Xenia; leads the spotlight (most recent)
            steam("1145360", "Hades", played = 2 * 3_600_000, fav = true, hours = 64),
            steam("367520", "Hollow Knight", played = day, fav = true, hours = 41),
            steam("504230", "Celeste", played = 3 * day, hours = 22),
            steam("413150", "Stardew Valley", played = 5 * day, hours = 120),
            steam("753640", "Outer Wilds", played = 9 * day, hours = 18),
            steam("2379780", "Balatro", played = 12 * 3_600_000, fav = true, hours = 33),
            steam("588650", "Dead Cells", hours = 12), steam("620", "Portal 2", hours = 9), steam("268910", "Cuphead"),
            steam("105600", "Terraria"), steam("1794680", "Vampire Survivors"), steam("990080", "Hogwarts Legacy"),
            rom("snes", "Super Mario World", played = 2 * day, fav = true), rom("snes", "Chrono Trigger"),
            rom("snes", "Super Metroid"), rom("n64", "Super Mario 64", played = 4 * day), rom("n64", "The Legend of Zelda: Ocarina of Time"),
            rom("gba", "Metroid Fusion"), rom("gba", "Pokemon Emerald Version"), rom("megadrive", "Sonic the Hedgehog 2"),
            rom("psx", "Castlevania: Symphony of the Night"), rom("psx", "Crash Bandicoot"),
        )
        // Fetch art once; later runs reuse the scratch data dir.
        if (app.library.value.map { it.id }.toSet() != games.map { it.id }.toSet() || app.library.value.any { it.assets.cover == null }) {
            app.library.update { games }
            app.scraper.scrape(app.library.value, force = true)
            val deadline = System.currentTimeMillis() + 240_000
            Thread.sleep(1500)
            while (app.scraper.progress.value.running && System.currentTimeMillis() < deadline) Thread.sleep(500)
            Thread.sleep(1000)
        }
        println("art: " + app.library.value.count { it.assets.cover != null } + "/" + games.size + " covers, " + app.library.value.count { it.assets.hero != null } + " heroes")

        app.mode.value = UiMode.BIGSCREEN
        val scene = ImageComposeScene(1920, 1080, Density(1.2f), coroutineContext = TestFrames) { App(app, showSplash = false) }
        var t = 0L
        fun pump(n: Int = 20) = repeat(n) { scene.renderTolerant(t); t += 16_000_000L; Thread.sleep(4) }
        fun settle() { repeat(60) { pump(2); Thread.sleep(15) } }
        fun press(nav: Nav, pad: Pad? = null) { app.controllers.onKey(nav, pad, true); pump(4); app.controllers.onKey(nav, pad, false); settle() }
        fun shot(name: String) = File(out, "$name.png").writeBytes(scene.renderTolerant(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)

        settle(); settle(); shot("big-screen-home")
        press(Nav.DOWN); press(Nav.RIGHT); shot("big-screen-shelf")
        press(Nav.CONFIRM, Pad.SOUTH); settle(); shot("big-screen-game")
        press(Nav.BACK, Pad.EAST); press(Nav.UP); press(Nav.UP); press(Nav.RIGHT); press(Nav.DOWN); shot("big-screen-library")
        app.mode.value = UiMode.XMB; settle(); press(Nav.RIGHT); press(Nav.DOWN); shot("xmb")
        app.mode.value = UiMode.LIST; settle(); shot("library")
        scene.close()
    }
}
