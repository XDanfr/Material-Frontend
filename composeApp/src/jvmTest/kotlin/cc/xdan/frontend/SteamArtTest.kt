package cc.xdan.frontend

import cc.xdan.frontend.data.SteamLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SteamArtTest {
    @Test fun nestedArtPrefersSharpHero() {
        val root = kotlin.io.path.createTempDirectory("steam").toFile()
        val app = File(root, "appcache/librarycache/3405340")
        // Newer client layout: hashed sub-folders; the blurred hero sits next to the real one.
        File(app, "4baa").apply { mkdirs(); File(this, "library_hero_blur.jpg").writeText("b"); File(this, "library_hero.jpg").writeText("h") }
        File(app, "8206").apply { mkdirs(); File(this, "library_capsule.jpg").writeText("c") }
        File(app, "2c68").apply { mkdirs(); File(this, "logo.png").writeText("l") }
        val a = SteamLibrary().localArt(root, "3405340")
        assertEquals("library_hero.jpg", File(a.hero!!).name)
        assertEquals("library_capsule.jpg", File(a.cover!!).name)
        File(app, "4baa/library_hero.jpg").delete()
        assertEquals(null, SteamLibrary().localArt(root, "3405340").hero, "never the pre-blurred copy")
        root.deleteRecursively()
    }
}
