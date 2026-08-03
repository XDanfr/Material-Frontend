package app.materialfrontend.data

import app.materialfrontend.model.Game
import app.materialfrontend.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class HostOs { WINDOWS, MAC, LINUX;
    companion object {
        val current: HostOs = System.getProperty("os.name").lowercase().let {
            when {
                it.contains("win") -> WINDOWS
                it.contains("mac") || it.contains("darwin") -> MAC
                else -> LINUX
            }
        }
        val arch: String = System.getProperty("os.arch").lowercase().let {
            if (it.contains("aarch64") || it.contains("arm64")) "arm64" else "x86_64"
        }
    }
}

object AppPaths {
    /** MF_DATA_DIR / -Dmf.dataDir override (portable installs, tests). */
    val root: File = (System.getProperty("mf.dataDir") ?: System.getenv("MF_DATA_DIR"))?.let(::File) ?: when (HostOs.current) {
        HostOs.WINDOWS -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MaterialFrontend")
        HostOs.MAC -> File(System.getProperty("user.home"), "Library/Application Support/MaterialFrontend")
        HostOs.LINUX -> File(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "material-frontend")
    }.also { it.mkdirs() }

    val cores = dir("cores")
    val system = dir("system")
    val saves = dir("saves")
    val states = dir("states")
    val media = dir("media")
    val cache = dir("cache")
    val settingsFile = File(root, "settings.json")
    val libraryFile = File(root, "library.json")

    private fun dir(name: String) = File(root, name).also { it.mkdirs() }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = true
}

/** A JSON-file-backed StateFlow that writes atomically, debounced. */
@OptIn(FlowPreview::class)
open class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    default: () -> T,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(load() ?: default())
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    init {
        _state.drop(1).debounce(400).onEach { save(it) }.launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    fun update(fn: (T) -> T) = _state.update(fn)

    private fun load(): T? = runCatching {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else null
    }.onFailure { System.err.println("Failed to read $file: ${it.message}") }.getOrNull()

    @Synchronized
    fun save(value: T = _state.value) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, value))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { System.err.println("Failed to write $file: ${it.message}") }
    }
}

class SettingsStore(scope: CoroutineScope) :
    JsonStore<Settings>(AppPaths.settingsFile, Settings.serializer(), { Settings() }, scope)

class LibraryStore(scope: CoroutineScope) :
    JsonStore<List<Game>>(AppPaths.libraryFile, ListSerializer(Game.serializer()), { emptyList() }, scope) {

    fun get(id: String): Game? = value.firstOrNull { it.id == id }

    fun updateGame(id: String, fn: (Game) -> Game) = update { list -> list.map { if (it.id == id) fn(it) else it } }

    fun upsertAll(games: List<Game>) = update { list ->
        val byId = list.associateBy { it.id }.toMutableMap()
        games.forEach { byId[it.id] = it }
        byId.values.toList()
    }

    fun remove(ids: Set<String>) = update { list -> list.filterNot { it.id in ids } }
}
