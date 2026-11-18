package cc.xdan.frontend

import cc.xdan.frontend.data.SteamAppInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Reads this machine's real appinfo.vdf when there is one (skipped elsewhere, e.g. CI). */
class SteamAppInfoLocalTest {
    @Test fun readsRealAppInfo() {
        val f = File(System.getProperty("user.home"), "Library/Application Support/Steam/appcache/appinfo.vdf").takeIf { it.isFile } ?: return
        val apps = SteamAppInfo.read(f)
        println("appinfo: ${apps.size} apps; e.g. " + apps.values.filter { it.type == "game" }.take(5).joinToString { "${it.id}=${it.name} ${it.oslist}" })
        assertTrue(apps.size > 100)
        assertTrue(apps.values.any { it.type == "game" && it.oslist.isNotEmpty() })
    }
}
