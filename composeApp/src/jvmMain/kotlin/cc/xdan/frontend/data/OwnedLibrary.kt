package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import cc.xdan.frontend.model.Platforms
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Games you own on Steam but haven't installed — shown greyed out with an Install button when this
 * machine can run them. Ownership comes from the Steam Web API when you've added a key (complete),
 * otherwise from the apps Steam lists in your local config (the ones you've played or opened).
 * Names and supported OSes come from Steam's local app cache; nothing is needed online for those.
 */
object SteamOwned {
    private val json = Json { ignoreUnknownKeys = true }

    /** Steam account folders (32-bit account ids) under userdata. */
    fun users(root: File): List<String> = File(root, "userdata").listFiles { f -> f.isDirectory && f.name.all(Char::isDigit) && f.name != "0" }
        .orEmpty().map { it.name }

    fun steamId64(id32: String): String = (76561197960265728L + id32.toLong()).toString()

    /** App ids under Software/Valve/Steam/apps in each user's localconfig.vdf. */
    fun localIds(root: File): Set<String> = users(root).flatMap { u ->
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val store = Vdf.parse(File(root, "userdata/$u/config/localconfig.vdf").readText())["UserLocalConfigStore"] as Map<String, Any>
            fun Map<String, Any>.ci(k: String) = entries.firstOrNull { it.key.equals(k, true) }?.value as? Map<String, Any>
            store.ci("Software")?.ci("Valve")?.ci("Steam")?.ci("apps")?.keys.orEmpty().filter { it.all(Char::isDigit) }
        }.getOrDefault(emptyList())
    }.toSet()

    data class Owned(val appId: String, val name: String?, val playtimeMinutes: Int = 0)

    /** IPlayerService/GetOwnedGames for every local account. */
    suspend fun webOwned(http: HttpClient, key: String, root: File): List<Owned> = users(root).flatMap { u ->
        runCatching {
            val body = http.get("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=$key&steamid=${steamId64(u)}&include_appinfo=1&include_played_free_games=1").bodyAsText()
            parseOwned(body)
        }.getOrDefault(emptyList())
    }

    fun parseOwned(body: String): List<Owned> =
        json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("games")?.jsonArray.orEmpty().mapNotNull { g ->
            val o = g.jsonObject
            val id = o["appid"]?.jsonPrimitive?.int?.toString() ?: return@mapNotNull null
            Owned(id, o["name"]?.jsonPrimitive?.content, o["playtime_forever"]?.jsonPrimitive?.intOrNull ?: 0)
        }

    /**
     * Not-installed games: owned ids minus [installedIds], kept only when appinfo says it's a game
     * (when appinfo knows the app). Each carries its supported OSes for the Install button.
     */
    fun notInstalled(owned: List<Owned>, installedIds: Set<String>, appInfo: Map<String, SteamAppInfo.App>, root: File?): List<Game> =
        owned.distinctBy { it.appId }.filter { it.appId !in installedIds }.mapNotNull { o ->
            val info = appInfo[o.appId]
            if (info != null && info.type.isNotEmpty() && info.type != "game") return@mapNotNull null
            val name = info?.name ?: o.name ?: return@mapNotNull null
            if (SteamLibrary.isTool(o.appId, name)) return@mapNotNull null
            Game(
                id = "steam-${o.appId}", title = name, platformId = Platforms.STEAM, kind = GameKind.STEAM, path = o.appId,
                playTimeSeconds = o.playtimeMinutes * 60L, installed = false, os = info?.oslist.orEmpty(),
                assets = root?.let { SteamLibrary().localArt(it, o.appId) } ?: cc.xdan.frontend.model.GameAssets(),
            )
        }
}

/** Where an owned game could be installed on this machine. */
sealed interface InstallTarget {
    val label: String
    data object HostSteam : InstallTarget { override val label = "Steam" }
    data class CrossOver(val bottle: String) : InstallTarget { override val label = "Steam in CrossOver ($bottle)" }
    data class Parallels(val vm: String) : InstallTarget { override val label = "Steam in Parallels ($vm)" }
}

object Installs {
    /**
     * Rules: Linux Steam installs Linux and Windows games (Proton); Windows installs Windows games;
     * macOS installs Mac games, and Windows games through Steam in CrossOver or Parallels when present.
     * An empty [os] (unknown) is offered to the host Steam.
     */
    fun targets(os: Set<String>, host: HostOs, crossoverBottles: List<String>, parallelsVms: List<String>): List<InstallTarget> {
        val unknown = os.isEmpty()
        return buildList {
            when (host) {
                HostOs.LINUX -> if (unknown || "linux" in os || "windows" in os) add(InstallTarget.HostSteam)
                HostOs.WINDOWS -> if (unknown || "windows" in os) add(InstallTarget.HostSteam)
                HostOs.MAC -> {
                    if (unknown || "macos" in os) add(InstallTarget.HostSteam)
                    if (unknown || "windows" in os) { crossoverBottles.forEach { add(InstallTarget.CrossOver(it)) }; parallelsVms.forEach { add(InstallTarget.Parallels(it)) } }
                }
            }
            if (host == HostOs.LINUX) crossoverBottles.forEach { if (unknown || "windows" in os) add(InstallTarget.CrossOver(it)) }
        }
    }

    /** (program, args) or a URI to open for installing [appId] with [t]. */
    fun command(t: InstallTarget, appId: String): Pair<String, List<String>>? = when (t) {
        InstallTarget.HostSteam -> null
        is InstallTarget.CrossOver -> (CompatSteam.crossoverWine()?.path ?: "wine") to listOf("--bottle", t.bottle, CompatSteam.STEAM_EXE, "steam://install/$appId")
        is InstallTarget.Parallels -> CompatSteam.PRLCTL to listOf("exec", t.vm, "--current-user", CompatSteam.STEAM_EXE, "steam://install/$appId")
    }
}
