package cc.xdan.frontend

import cc.xdan.frontend.data.RetroAchievements
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetroAchievementsTest {
    private val body = ByteArray(8192) { (it * 7).toByte() }

    @Test fun headersAreStripped() {
        val ines = byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 0x1A) + ByteArray(12) + body
        assertEquals(RetroAchievements.md5(body), RetroAchievements.md5(RetroAchievements.normalize(ines, "nes")))
        val smc = ByteArray(512) + body
        assertEquals(RetroAchievements.md5(body), RetroAchievements.md5(RetroAchievements.normalize(smc, "snes")))
        assertEquals(RetroAchievements.md5(body), RetroAchievements.md5(RetroAchievements.normalize(body, "gba")))
    }

    @Test fun n64ByteOrders() {
        val z64 = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40) + ByteArray(60) { it.toByte() }
        val v64 = z64.copyOf().also { for (i in it.indices step 2) { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t } }
        val n64 = z64.copyOf().also { for (i in it.indices step 4) { it.reverse(i, i + 4) } }
        val want = RetroAchievements.md5(z64)
        assertEquals(want, RetroAchievements.md5(RetroAchievements.normalize(v64, "n64")))
        assertEquals(want, RetroAchievements.md5(RetroAchievements.normalize(n64, "n64")))
    }

    @Test fun zipAndUnsupported() {
        val dir = kotlin.io.path.createTempDirectory("ra").toFile()
        val z = File(dir, "g.zip")
        ZipOutputStream(z.outputStream()).use { it.putNextEntry(ZipEntry("g.gb")); it.write(body); it.closeEntry() }
        assertEquals(RetroAchievements.md5(body), RetroAchievements.hash(z, "gb"))
        assertNull(RetroAchievements.hash(z, "psx"), "disc systems aren't hashed")
        dir.deleteRecursively()
    }

    @Test fun parsesProgress() {
        val json = """{"Title":"Test Game","Achievements":{
            "1":{"Title":"First","Description":"d","Points":5,"BadgeName":"111","DateEarned":"2024-01-01 10:00:00","DateEarnedHardcore":""},
            "2":{"Title":"Second","Description":"d","Points":10,"BadgeName":"222"}}}"""
        val p = RetroAchievements.parseProgress(7, json)!!
        assertEquals(1, p.earned); assertEquals(2, p.total); assertEquals(5, p.points); assertEquals(15, p.totalPoints)
        assertEquals("https://media.retroachievements.org/Badge/222_lock.png", p.achievements.last().badgeUrl)
    }
}
