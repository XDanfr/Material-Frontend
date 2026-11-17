package app.materialfrontend

import app.materialfrontend.emulators.CoreCatalog
import app.materialfrontend.emulators.CoreInstaller
import app.materialfrontend.input.ControllerManager
import app.materialfrontend.libretro.EmulationSession
import app.materialfrontend.scraper.createHttpClient
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in (MF_CORE_TESTS=1): downloads the tiny "2048" libretro core (runs without content) and
 * checks that frames come out of the FFM host.
 */
class LibretroSmokeTest {
    @Test
    fun runs2048Core() {
        if (System.getenv("MF_CORE_TESTS") == null) return
        val file = CoreCatalog.installedFile("2048") ?: runBlocking { CoreInstaller(createHttpClient()).install("2048").getOrThrow() }
        val session = EmulationSession(file, null, "smoke-2048", ControllerManager(), File(System.getProperty("java.io.tmpdir")), rewindEnabled = true)
        session.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (session.frameCounter < 30 && System.currentTimeMillis() < deadline && session.error.value == null) Thread.sleep(50)
        val err = session.error.value
        session.stop()
        assertTrue(err == null, "core error: $err")
        assertTrue(session.frameCounter >= 30, "only ${session.frameCounter} frames")
        assertNotNull(session.snapshot())
    }
}
