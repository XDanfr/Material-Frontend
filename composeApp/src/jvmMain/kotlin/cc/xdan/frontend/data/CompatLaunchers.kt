package cc.xdan.frontend.data

import cc.xdan.frontend.model.Game
import cc.xdan.frontend.model.GameKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.security.MessageDigest

/**
 * Windows launchers (Epic, EA, GOG, Ubisoft, Xbox) inside CrossOver bottles and Parallels VMs.
 * Everything is read from the Windows drives as the host sees them — nothing runs to scan.
 *
 * Launching:
 * - CrossOver: `wine --bottle <b> start.exe …` (ShellExecute, so launcher URIs and plain .exe both work).
 * - Parallels: `prlctl exec <vm> --current-user explorer.exe …` (opens URIs, shell:AppsFolder and .exe).
 */
object CompatLaunchers {
    private val json = Json { ignoreUnknownKeys = true }

    /** One Windows install seen from the host. */
    class WinEnv(
        val idPrefix: String,
        /** Drive letter (lowercase) → host folder. */
        val drives: Map<Char, File>,
        /** Wine registry (system.reg) for CrossOver; null for Parallels. */
        val systemReg: File?,
        /** Turns a ShellExecute target (URI / Windows path) into (program, args). */
        val launch: (target: String, workDir: String?) -> Pair<String, String>,
    ) {
        fun toWin(f: File): String? {
            val p = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
            for ((letter, root) in drives) {
                val r = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
                if (p == r || p.startsWith("$r/")) return "${letter.uppercaseChar()}:\\" + p.removePrefix(r).trimStart('/').replace('/', '\\')
            }
            return null
        }
    }

    // ---------------- environments ----------------

    fun crossoverEnvs(bottles: File = CompatSteam.crossoverBottlesDir(), wine: File? = CompatSteam.crossoverWine()): List<WinEnv> =
        bottles.listFiles { f -> File(f, "drive_c").isDirectory }.orEmpty().map { bottle ->
            val drives = sortedMapOf('c' to File(bottle, "drive_c"))
            File(bottle, "dosdevices").listFiles().orEmpty().forEach { link ->
                val m = Regex("^([a-y]):$").find(link.name) ?: return@forEach
                val letter = m.groupValues[1][0]
                if (letter == 'c' || letter == 'y') return@forEach   // y: is usually the whole home folder
                runCatching { link.canonicalFile }.getOrNull()?.takeIf { it.isDirectory && it.path != "/" }?.let { drives[letter] = it }
            }
            WinEnv("crossover-${'$'}{SRC}-${Ids.of(bottle.name)}-", drives, File(bottle, "system.reg").takeIf { it.isFile }) { target, dir ->
                (wine?.path ?: "wine") to buildString {
                    append("--bottle \"${bottle.name}\" start.exe ")
                    if (dir != null) append("/d \"$dir\" ")
                    append("\"$target\"")
                }
            }
        }

    fun parallelsEnvs(volumes: File = File("/Volumes"), prlctl: String = "/usr/local/bin/prlctl"): List<WinEnv> {
        val all = volumes.listFiles { f -> f.isDirectory && Regex("^\\[[A-Z]] ").containsMatchIn(f.name) }.orEmpty()
        return all.groupBy { it.name.substringAfter("] ").removeSuffix(".hidden").trim() }.map { (vm, vols) ->
            val drives = vols.associate { it.name[1].lowercaseChar() to it }.toSortedMap()
            WinEnv("parallels-${'$'}{SRC}-${Ids.of(vm)}-", drives, null) { target, _ ->
                prlctl to "exec \"$vm\" --current-user explorer.exe \"$target\""
            }
        }
    }

    fun crossover(): List<Game> = crossoverEnvs().flatMap(::scan)
    fun parallels(): List<Game> = parallelsEnvs().flatMap(::scan)

    fun scan(env: WinEnv): List<Game> = listOf<(WinEnv) -> List<Game>>(::epic, ::ea, ::gog, ::ubisoft, ::xbox)
        .flatMap { f -> runCatching { f(env) }.onFailure { System.err.println("Compat launcher scan: ${it.message}") }.getOrDefault(emptyList()) }

    private fun id(env: WinEnv, source: String, rest: String) = env.idPrefix.replace("${'$'}{SRC}", source) + rest

    private fun launcherGame(env: WinEnv, source: String, key: String, title: String, platform: String, target: String, dir: String? = null): Game {
        val (path, args) = env.launch(target, dir)
        return Game(id = id(env, source, key), title = title, platformId = platform, kind = GameKind.LAUNCHER, path = path, launchArgs = args)
    }

    // ---------------- scanners ----------------

    fun epic(env: WinEnv): List<Game> {
        val dir = env.drives['c']?.let { File(it, "ProgramData/Epic/EpicGamesLauncher/Data/Manifests") }?.takeIf { it.isDirectory } ?: return emptyList()
        return LauncherSources.epic(listOf(dir)).map { g -> launcherGame(env, "epic", g.id.removePrefix("epic-"), g.title, "epic", g.path) }
    }

    fun ea(env: WinEnv): List<Game> {
        val roots = env.drives.values.flatMap { d ->
            listOf("Program Files/EA Games", "Program Files (x86)/Origin Games", "Program Files/Electronic Arts", "EA Games", "Origin Games").map { File(d, it) }
        }.filter { it.isDirectory }
        return LauncherSources.ea(roots).mapNotNull { g ->
            val exe = File(g.path); val win = env.toWin(exe) ?: return@mapNotNull null
            launcherGame(env, "ea", Ids.of(win.lowercase()), g.title, "ea", win, env.toWin(exe.parentFile))
        }
    }

    /** GOG installs carry goggame-<id>.info (JSON with the name and play tasks), no registry needed. */
    fun gog(env: WinEnv): List<Game> {
        val roots = env.drives.values.flatMap { d ->
            listOf("GOG Games", "Program Files (x86)/GOG Galaxy/Games", "Program Files (x86)/GOG Games", "Program Files/GOG Games").map { File(d, it) }
        }.filter { it.isDirectory }
        return roots.flatMap { it.listFiles { f -> f.isDirectory }.orEmpty().toList() }.mapNotNull { dir ->
            val info = dir.listFiles { f -> f.name.startsWith("goggame-") && f.name.endsWith(".info") }?.firstOrNull() ?: return@mapNotNull null
            runCatching {
                val o = json.parseToJsonElement(info.readText()).jsonObject
                val gameId = o["gameId"]?.jsonPrimitive?.content ?: info.name.removePrefix("goggame-").removeSuffix(".info")
                if (o["rootGameId"]?.jsonPrimitive?.content.let { it != null && it != gameId }) return@runCatching null   // DLC
                val name = o["name"]?.jsonPrimitive?.content ?: dir.name
                val task = (o["playTasks"] as? JsonArray)?.map { it.jsonObject }?.firstOrNull { it["isPrimary"]?.jsonPrimitive?.booleanOrNull == true }
                    ?: return@runCatching null
                val rel = task["path"]?.jsonPrimitive?.content ?: return@runCatching null
                val exe = File(dir, rel.replace('\\', '/')).takeIf { it.isFile } ?: return@runCatching null
                val work = task["workingDir"]?.jsonPrimitive?.content?.let { File(dir, it.replace('\\', '/')) } ?: exe.parentFile
                val win = env.toWin(exe) ?: return@runCatching null
                launcherGame(env, "gog", gameId, name, "gog", win, env.toWin(work))
            }.getOrNull()
        }
    }

    /** Ubisoft Connect keeps installs only in the registry, so this works in CrossOver (text registry) only. */
    fun ubisoft(env: WinEnv): List<Game> {
        val reg = env.systemReg ?: return emptyList()
        return WineRegistry.parse(reg.readText()).entries.mapNotNull { (key, values) ->
            val m = Regex("""(?i)^software\\(?:wow6432node\\)?ubisoft\\launcher\\installs\\(\d+)$""").find(key) ?: return@mapNotNull null
            val dir = values["InstallDir"] ?: return@mapNotNull null
            val name = dir.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
            launcherGame(env, "ubisoft", m.groupValues[1], name, "ubisoft", "uplay://launch/${m.groupValues[1]}/0")
        }.distinctBy { it.id }
    }

    /**
     * Xbox (GDK) games installed to <drive>:\XboxGames (the Xbox app's default) — readable from a
     * Parallels shared drive. Launched through shell:AppsFolder with the package family name.
     */
    fun xbox(env: WinEnv): List<Game> = env.drives.values.flatMap { d ->
        File(d, "XboxGames").listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { game ->
            val cfg = listOf("Content/MicrosoftGame.config", "MicrosoftGame.config").map { File(game, it) }.firstOrNull { it.isFile } ?: return@mapNotNull null
            runCatching {
                val x = cfg.readText()
                val identity = Regex("<Identity\\b[^>]*>").find(x)?.value ?: return@runCatching null
                val name = Regex("\\bName=\"([^\"]+)\"").find(identity)?.groupValues?.get(1) ?: return@runCatching null
                val publisher = Regex("\\bPublisher=\"([^\"]+)\"").find(identity)?.groupValues?.get(1)?.let(::unescapeXml) ?: return@runCatching null
                val appId = Regex("<Executable\\b[^>]*\\bId=\"([^\"]+)\"").find(x)?.groupValues?.get(1) ?: "Game"
                val title = Regex("DefaultDisplayName=\"([^\"]+)\"").find(x)?.groupValues?.get(1)?.takeUnless { it.startsWith("ms-resource:") }?.let(::unescapeXml)
                    ?: game.name
                val pfn = "${name}_${publisherId(publisher)}"
                launcherGame(env, "xbox", pfn, title, "xbox-pc", "shell:AppsFolder\\$pfn!$appId")
            }.getOrNull()
        }
    }

    private fun unescapeXml(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /** Windows package publisher id: first 8 bytes of SHA-256(UTF-16LE publisher), base32 (Crockford-style) → 13 chars. */
    fun publisherId(publisher: String): String {
        val h = MessageDigest.getInstance("SHA-256").digest(publisher.toByteArray(Charsets.UTF_16LE))
        var bits = 0L
        for (i in 0 until 8) bits = (bits shl 8) or (h[i].toLong() and 0xff)
        val alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
        // 64 bits + one 0 pad bit = 65 = 13 × 5.
        return buildString {
            for (i in 0 until 13) {
                val shift = 59 - 5 * i
                val v = if (shift >= 0) (bits ushr shift) and 31 else (bits shl -shift) and 31
                append(alphabet[v.toInt()])
            }
        }
    }
}

/** Wine's text registry files (system.reg / user.reg). Keys and values unescaped. */
object WineRegistry {
    fun parse(text: String): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, MutableMap<String, String>>()
        var cur: MutableMap<String, String>? = null
        for (line in text.lineSequence()) {
            if (line.startsWith("[")) {
                val key = line.substring(1, line.indexOf(']').coerceAtLeast(1)).let(::unescape)
                cur = out.getOrPut(key) { LinkedHashMap() }
                continue
            }
            val m = Regex("^\"((?:[^\"\\\\]|\\\\.)*)\"=\"((?:[^\"\\\\]|\\\\.)*)\"").find(line) ?: continue
            cur?.put(unescape(m.groupValues[1]), unescape(m.groupValues[2]))
        }
        return out
    }

    private fun unescape(s: String) = s.replace(Regex("\\\\(.)")) { it.groupValues[1] }
}
