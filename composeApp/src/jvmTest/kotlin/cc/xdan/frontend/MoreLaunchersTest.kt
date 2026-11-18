package cc.xdan.frontend

import cc.xdan.frontend.data.HostOs
import cc.xdan.frontend.data.MoreLaunchers
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MoreLaunchersTest {
    private fun ld(field: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(); out.write((field shl 3) or 2)
        var n = body.size; while (n >= 0x80) { out.write((n and 0x7F) or 0x80); n = n ushr 7 }; out.write(n)
        out.write(body); return out.toByteArray()
    }

    @Test fun battleNetProductDb() {
        fun install(uid: String, path: String) = ld(1, ld(1, uid.toByteArray()) + ld(2, uid.toByteArray()) + ld(3, ld(1, path.toByteArray())))
        val db = install("wow", "/Applications/World of Warcraft") + install("agent", "") + install("fenris", "C:\\Diablo IV")
        assertEquals(listOf("wow" to "/Applications/World of Warcraft", "agent" to "", "fenris" to "C:\\Diablo IV"), MoreLaunchers.parseProductDb(db))
        val f = kotlin.io.path.createTempFile("product", ".db").toFile().apply { writeBytes(install("agent", "") + install("s2", "")) }
        val games = MoreLaunchers.battleNet(f)
        assertEquals(listOf("battlenet-s2" to "battlenet://S2"), games.map { it.id to it.path }, "known games only; the agent is skipped")
        f.delete()
    }

    @Test fun itchReceipts() {
        val root = kotlin.io.path.createTempDirectory("itch").toFile()
        val dir = File(root, "cool-game").apply { mkdirs() }
        File(dir, ".itch").mkdirs()
        GZIPOutputStream(File(dir, ".itch/receipt.json.gz").outputStream()).use { it.write("""{"game":{"id":42,"title":"Cool Game"}}""".toByteArray()) }
        File(dir, "Cool Game.app/Contents").mkdirs()
        File(dir, "CoolGame.exe").writeText("x"); File(dir, "unins000.exe").writeText("x")
        val mac = MoreLaunchers.itch(listOf(root), HostOs.MAC).single()
        assertEquals("itch-42", mac.id); assertEquals("Cool Game", mac.title)
        assert(mac.path.startsWith("file:") && mac.path.trimEnd('/').endsWith("Cool%20Game.app")) { mac.path }
        assertEquals("CoolGame.exe", File(MoreLaunchers.itch(listOf(root), HostOs.WINDOWS).single().path).name)
        root.deleteRecursively()
    }
}
