package cc.xdan.frontend

import cc.xdan.frontend.data.LauncherSources
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeroicOwnedTest {
    @Test fun ownedNotInstalledFromStoreCaches() {
        val dir = kotlin.io.path.createTempDirectory("heroic").toFile()
        File(dir, "store_cache").mkdirs()
        File(dir, "store_cache/legendary_library.json").writeText("""{"library":[
            {"app_name":"Fortnite","title":"Fortnite","is_installed":false},
            {"app_name":"Hades","title":"Hades","is_installed":true},
            {"app_name":"SomeDlc","title":"DLC","is_dlc":true}]}""")
        File(dir, "store_cache/gog_library.json").writeText("""{"games":[{"app_name":"1207658924","title":"Unreal Gold","is_installed":false}]}""")
        val owned = LauncherSources.heroicOwned(installedIds = setOf("gog-1207658924x"), dirs = listOf(dir))
        assertEquals(setOf("epic-Fortnite", "gog-1207658924"), owned.map { it.id }.toSet())
        assertTrue(owned.all { !it.installed && it.path.startsWith("heroic://launch?appName=") })
        assertEquals(emptyList(), LauncherSources.heroicOwned(setOf("epic-Fortnite", "gog-1207658924"), listOf(dir)).map { it.id })
        dir.deleteRecursively()
    }
}
