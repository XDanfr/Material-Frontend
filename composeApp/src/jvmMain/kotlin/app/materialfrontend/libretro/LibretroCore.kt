package app.materialfrontend.libretro

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BOOLEAN
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path

/** libretro.h constants used by the host. */
object Retro {
    const val API_VERSION = 1
    const val DEVICE_JOYPAD = 1
    const val DEVICE_ANALOG = 5
    const val DEVICE_ID_JOYPAD_MASK = 256
    const val MEMORY_SAVE_RAM = 0
    const val PIXEL_0RGB1555 = 0
    const val PIXEL_XRGB8888 = 1
    const val PIXEL_RGB565 = 2
    const val EXPERIMENTAL = 0x10000

    const val ENV_SET_ROTATION = 1
    const val ENV_GET_OVERSCAN = 2
    const val ENV_GET_CAN_DUPE = 3
    const val ENV_SET_MESSAGE = 6
    const val ENV_SHUTDOWN = 7
    const val ENV_SET_PERFORMANCE_LEVEL = 8
    const val ENV_GET_SYSTEM_DIRECTORY = 9
    const val ENV_SET_PIXEL_FORMAT = 10
    const val ENV_SET_INPUT_DESCRIPTORS = 11
    const val ENV_SET_HW_RENDER = 14
    const val ENV_GET_VARIABLE = 15
    const val ENV_SET_VARIABLES = 16
    const val ENV_GET_VARIABLE_UPDATE = 17
    const val ENV_SET_SUPPORT_NO_GAME = 18
    const val ENV_GET_LIBRETRO_PATH = 19
    const val ENV_GET_RUMBLE_INTERFACE = 23
    const val ENV_GET_INPUT_DEVICE_CAPABILITIES = 24
    const val ENV_GET_LOG_INTERFACE = 27
    const val ENV_GET_CORE_ASSETS_DIRECTORY = 30
    const val ENV_GET_SAVE_DIRECTORY = 31
    const val ENV_SET_SYSTEM_AV_INFO = 32
    const val ENV_SET_SUBSYSTEM_INFO = 34
    const val ENV_SET_CONTROLLER_INFO = 35
    const val ENV_SET_MEMORY_MAPS = 36
    const val ENV_SET_GEOMETRY = 37
    const val ENV_GET_USERNAME = 38
    const val ENV_GET_LANGUAGE = 39
    const val ENV_SET_SUPPORT_ACHIEVEMENTS = 42
    const val ENV_SET_SERIALIZATION_QUIRKS = 44
    const val ENV_GET_AUDIO_VIDEO_ENABLE = 47
    const val ENV_GET_FASTFORWARDING = 49
    const val ENV_GET_INPUT_BITMASKS = 51
    const val ENV_GET_CORE_OPTIONS_VERSION = 52
    const val ENV_SET_CONTENT_INFO_OVERRIDE = 65
    const val ENV_SET_CORE_OPTIONS_DISPLAY = 55
    const val ENV_GET_MESSAGE_INTERFACE_VERSION = 59
    const val ENV_SET_DISK_CONTROL_INTERFACE = 13
    const val ENV_SET_CORE_OPTIONS = 53
    const val ENV_SET_CORE_OPTIONS_INTL = 54
    const val ENV_GET_DISK_CONTROL_INTERFACE_VERSION = 57
    const val ENV_SET_DISK_CONTROL_EXT_INTERFACE = 58
    const val ENV_SET_CORE_OPTIONS_V2 = 67
    const val ENV_SET_CORE_OPTIONS_V2_INTL = 68
    const val ENV_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK = 69
    const val ENV_SET_VARIABLE = 70
}

/** Callbacks a core makes into the host (always on the emulation thread). */
interface LibretroHost {
    fun environment(cmd: Int, data: MemorySegment): Boolean
    fun videoRefresh(data: MemorySegment, width: Int, height: Int, pitch: Long)
    fun audioSample(left: Short, right: Short)
    fun audioSampleBatch(data: MemorySegment, frames: Long): Long
    fun inputPoll()
    fun inputState(port: Int, device: Int, index: Int, id: Int): Short
    fun rumble(port: Int, effect: Int, strength: Short): Boolean
}

data class SystemInfo(val libraryName: String, val libraryVersion: String, val validExtensions: String, val needFullpath: Boolean, val blockExtract: Boolean)
data class AvInfo(val baseWidth: Int, val baseHeight: Int, val maxWidth: Int, val maxHeight: Int, val aspectRatio: Float, val fps: Double, val sampleRate: Double)

/**
 * Binds a libretro shared library with the Foreign Function & Memory API — no JNI glue to build.
 * All calls must happen on one thread.
 */
class LibretroCore(libPath: Path, private val host: LibretroHost) : AutoCloseable {
    val arena: Arena = Arena.ofShared()
    private val linker = Linker.nativeLinker()
    private val lookup = SymbolLookup.libraryLookup(libPath, arena)

    private fun fn(name: String, desc: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.find(name).orElseThrow { IllegalStateException("Core is missing $name") }, desc)

    private val hSetEnvironment = fn("retro_set_environment", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetVideo = fn("retro_set_video_refresh", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetAudio = fn("retro_set_audio_sample", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetAudioBatch = fn("retro_set_audio_sample_batch", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetInputPoll = fn("retro_set_input_poll", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetInputState = fn("retro_set_input_state", FunctionDescriptor.ofVoid(ADDRESS))
    private val hInit = fn("retro_init", FunctionDescriptor.ofVoid())
    private val hDeinit = fn("retro_deinit", FunctionDescriptor.ofVoid())
    private val hApiVersion = fn("retro_api_version", FunctionDescriptor.of(JAVA_INT))
    private val hSystemInfo = fn("retro_get_system_info", FunctionDescriptor.ofVoid(ADDRESS))
    private val hAvInfo = fn("retro_get_system_av_info", FunctionDescriptor.ofVoid(ADDRESS))
    private val hSetPortDevice = fn("retro_set_controller_port_device", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT))
    private val hReset = fn("retro_reset", FunctionDescriptor.ofVoid())
    private val hRun = fn("retro_run", FunctionDescriptor.ofVoid())
    private val hSerializeSize = fn("retro_serialize_size", FunctionDescriptor.of(JAVA_LONG))
    private val hSerialize = fn("retro_serialize", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_LONG))
    private val hUnserialize = fn("retro_unserialize", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_LONG))
    private val hLoadGame = fn("retro_load_game", FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS))
    private val hUnloadGame = fn("retro_unload_game", FunctionDescriptor.ofVoid())
    private val hMemData = fn("retro_get_memory_data", FunctionDescriptor.of(ADDRESS, JAVA_INT))
    private val hMemSize = fn("retro_get_memory_size", FunctionDescriptor.of(JAVA_LONG, JAVA_INT))

    /** Upcall stubs must stay alive as long as the core may call them. */
    private val callbacks = Callbacks(host)
    val rumbleStub: MemorySegment

    init {
        val l = MethodHandles.lookup()
        fun stub(name: String, type: MethodType, desc: FunctionDescriptor): MemorySegment =
            linker.upcallStub(l.findVirtual(Callbacks::class.java, name, type).bindTo(callbacks), desc, arena)

        val env = stub("env", MethodType.methodType(Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, MemorySegment::class.java),
            FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS))
        val video = stub("video", MethodType.methodType(Void.TYPE, MemorySegment::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType),
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG))
        val audio = stub("audio", MethodType.methodType(Void.TYPE, Short::class.javaPrimitiveType, Short::class.javaPrimitiveType),
            FunctionDescriptor.ofVoid(JAVA_SHORT, JAVA_SHORT))
        val audioBatch = stub("audioBatch", MethodType.methodType(Long::class.javaPrimitiveType, MemorySegment::class.java, Long::class.javaPrimitiveType),
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG))
        val poll = stub("poll", MethodType.methodType(Void.TYPE), FunctionDescriptor.ofVoid())
        val state = stub("state", MethodType.methodType(Short::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            FunctionDescriptor.of(JAVA_SHORT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT))
        rumbleStub = stub("rumble", MethodType.methodType(Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Short::class.javaPrimitiveType),
            FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, JAVA_INT, JAVA_SHORT))

        hSetEnvironment.invoke(env)
        hSetVideo.invoke(video)
        hSetAudio.invoke(audio)
        hSetAudioBatch.invoke(audioBatch)
        hSetInputPoll.invoke(poll)
        hSetInputState.invoke(state)
    }

    fun apiVersion(): Int = hApiVersion.invoke() as Int
    fun init() { hInit.invoke() }
    fun deinit() { hDeinit.invoke() }
    fun reset() { hReset.invoke() }
    fun run() { hRun.invoke() }
    fun unloadGame() { hUnloadGame.invoke() }
    fun setPortDevice(port: Int, device: Int) { hSetPortDevice.invoke(port, device) }

    fun systemInfo(): SystemInfo = Arena.ofConfined().use { a ->
        val s = a.allocate(32)
        hSystemInfo.invoke(s)
        SystemInfo(
            cstr(s.get(ADDRESS, 0)) ?: "core", cstr(s.get(ADDRESS, 8)) ?: "", cstr(s.get(ADDRESS, 16)) ?: "",
            s.get(JAVA_BOOLEAN, 24), s.get(JAVA_BOOLEAN, 25),
        )
    }

    fun avInfo(): AvInfo = Arena.ofConfined().use { a ->
        val s = a.allocate(40, 8)
        hAvInfo.invoke(s)
        readAv(s)
    }

    /** Loads content. Passes the path and, unless the core wants a full path, the bytes too. */
    fun loadGame(path: String?, data: ByteArray?): Boolean {
        val info = arena.allocate(32, 8)
        if (path != null) info.set(ADDRESS, 0, arena.allocateFrom(path))
        if (data != null) {
            val buf = arena.allocate(data.size.toLong().coerceAtLeast(1), 16)
            MemorySegment.copy(data, 0, buf, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, data.size)
            info.set(ADDRESS, 8, buf); info.set(JAVA_LONG, 16, data.size.toLong())
        }
        return hLoadGame.invoke(if (path == null && data == null) MemorySegment.NULL else info) as Boolean
    }

    fun serializeSize(): Long = hSerializeSize.invoke() as Long

    fun serialize(): ByteArray? {
        val size = serializeSize(); if (size <= 0) return null
        return Arena.ofConfined().use { a ->
            val buf = a.allocate(size, 16)
            if (hSerialize.invoke(buf, size) as Boolean) buf.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE) else null
        }
    }

    fun unserialize(state: ByteArray): Boolean = Arena.ofConfined().use { a ->
        val buf = a.allocate(state.size.toLong(), 16)
        MemorySegment.copy(state, 0, buf, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, state.size)
        hUnserialize.invoke(buf, state.size.toLong()) as Boolean
    }

    fun memory(type: Int): MemorySegment? {
        val size = hMemSize.invoke(type) as Long
        val ptr = hMemData.invoke(type) as MemorySegment
        if (size <= 0 || ptr.address() == 0L) return null
        return ptr.reinterpret(size)
    }

    override fun close() { runCatching { arena.close() } }

    companion object {
        fun cstr(p: MemorySegment): String? = if (p.address() == 0L) null else p.reinterpret(Long.MAX_VALUE).getString(0)

        fun readAv(s: MemorySegment) = AvInfo(
            s.get(JAVA_INT, 0), s.get(JAVA_INT, 4), s.get(JAVA_INT, 8), s.get(JAVA_INT, 12),
            s.get(java.lang.foreign.ValueLayout.JAVA_FLOAT, 16),
            s.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 24), s.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 32),
        )
    }
}

/** Upcall targets. Exceptions must never escape into native code. */
class Callbacks(private val host: LibretroHost) {
    fun env(cmd: Int, data: MemorySegment): Boolean = try { host.environment(cmd, data) } catch (t: Throwable) { t.printStackTrace(); false }
    fun video(data: MemorySegment, w: Int, h: Int, pitch: Long) { try { host.videoRefresh(data, w, h, pitch) } catch (t: Throwable) { t.printStackTrace() } }
    fun audio(l: Short, r: Short) { try { host.audioSample(l, r) } catch (_: Throwable) {} }
    fun audioBatch(data: MemorySegment, frames: Long): Long = try { host.audioSampleBatch(data, frames) } catch (_: Throwable) { frames }
    fun poll() { try { host.inputPoll() } catch (_: Throwable) {} }
    fun state(port: Int, device: Int, index: Int, id: Int): Short = try { host.inputState(port, device, index, id) } catch (_: Throwable) { 0 }
    fun rumble(port: Int, effect: Int, strength: Short): Boolean = try { host.rumble(port, effect, strength) } catch (_: Throwable) { false }
}
