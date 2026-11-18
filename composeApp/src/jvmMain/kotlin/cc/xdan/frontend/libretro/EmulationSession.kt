package cc.xdan.frontend.libretro

import cc.xdan.frontend.data.AppPaths
import cc.xdan.frontend.input.ControllerManager
import cc.xdan.frontend.input.Pad
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipFile
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** A finished video frame in BGRA (Skia N32 on little-endian). */
class Frame(val pixels: ByteArray, val width: Int, val height: Int)

data class SaveSlot(val slot: Int, val file: File, val thumbnail: File, val modified: Long)

/**
 * Runs one libretro core + content on a dedicated thread: frame pacing, audio, input, SRAM,
 * save states, rewind and fast-forward.
 */
class EmulationSession(
    private val coreFile: File,
    private val romPath: String?,
    val gameId: String,
    private val controllers: ControllerManager,
    private val systemDir: File,
    private val rewindEnabled: Boolean,
    /** Which pad button drives each RetroPad id (see InputMaps). */
    inputMap: List<Pad> = cc.xdan.frontend.input.InputMaps.DEFAULT,
    val platformId: String = "",
    /** Load the auto-save (slot 0) after the first frame — "resume where you left off". */
    private val resumeAutoSave: Boolean = false,
) : LibretroHost {
    enum class Status { LOADING, RUNNING, PAUSED, ERROR, STOPPED }

    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile var frame: Frame? = null; private set
    @Volatile var frameCounter = 0L; private set
    @Volatile var aspectRatio = 4f / 3f; private set
    @Volatile var fastForward = false
    @Volatile var rewinding = false
    @Volatile var paused = false

    private lateinit var core: LibretroCore

    // ---- Hardware rendering (OpenGL) ----
    private var hwRequest: HwRenderRequest? = null
    private var gl: GlRenderer? = null
    override fun hwFramebuffer(): Long = gl?.fbo?.toLong() ?: 0L
    override fun hwProcAddress(name: String): Long = gl?.procAddress(name) ?: 0L

    /** Calls a void(void) function pointer the core gave us (context_reset / context_destroy). */
    private fun callVoid(fn: MemorySegment) {
        if (fn.address() == 0L) return
        java.lang.foreign.Linker.nativeLinker().downcallHandle(fn, java.lang.foreign.FunctionDescriptor.ofVoid()).invoke()
    }

    /** Creates the GL context + framebuffer after load_game, then lets the core set up its GL state. */
    private fun startHwRender(a: AvInfo) {
        val req = hwRequest ?: return
        val r = runCatching { GlRenderer.create(req.coreProfile) }.getOrElse { throw IllegalStateException("Couldn't start OpenGL for this core: ${it.message}. Try “Launch with → RetroArch”.") }
        gl = r
        r.ensureFramebuffer(a.maxWidth.coerceAtLeast(a.baseWidth), a.maxHeight.coerceAtLeast(a.baseHeight), req.depth, req.stencil)
        callVoid(req.contextReset)
    }

    private fun stopHwRender() {
        val req = hwRequest ?: return
        runCatching { callVoid(req.contextDestroy) }
        gl?.destroy(); gl = null
    }
    private var pixelFormat = Retro.PIXEL_0RGB1555
    private var av: AvInfo? = null
    private var audio: SourceDataLine? = null
    private val audioBuf = ByteArray(1 shl 16)
    private var singleSamples = ByteArray(8192); private var singleLen = 0
    private val variables = LinkedHashMap<String, String>()
    private var variablesDirty = false

    // ---- Core options (legacy / v1 / v2), saved per core ----
    private val optionStore = CoreOptionStore(coreFile.nameWithoutExtension)
    private val savedOptions = optionStore.load()
    private val _options = MutableStateFlow<List<CoreOption>>(emptyList())
    val options: StateFlow<List<CoreOption>> = _options.asStateFlow()
    private val _optionValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val optionValues: StateFlow<Map<String, String>> = _optionValues.asStateFlow()

    private fun registerOptions(list: List<CoreOption>) {
        if (list.isEmpty()) return
        val byKey = LinkedHashMap<String, CoreOption>().apply { _options.value.forEach { put(it.key, it) }; list.forEach { put(it.key, it) } }
        list.forEach { o ->
            val saved = savedOptions[o.key]?.takeIf { s -> o.values.any { it.first == s } }
            variables[o.key] = saved ?: variables[o.key]?.takeIf { v -> o.values.any { it.first == v } } ?: o.default
        }
        _options.value = byKey.values.toList()
        _optionValues.value = HashMap(variables)
    }

    /** Change an option from the UI; applied on the emulation thread and remembered for this core. */
    fun setOption(key: String, value: String) = post {
        variables[key] = value; variablesDirty = true
        _optionValues.value = HashMap(variables)
        optionStore.save(variables.filter { (k, v) -> _options.value.firstOrNull { it.key == k }?.default != v })
    }

    fun resetOptions() = post {
        _options.value.forEach { variables[it.key] = it.default }; variablesDirty = true
        _optionValues.value = HashMap(variables); optionStore.save(emptyMap())
    }

    // ---- Multi-disc ----
    private var disk: DiskControl? = null
    private val _discs = MutableStateFlow<DiscState?>(null)
    val discs: StateFlow<DiscState?> = _discs.asStateFlow()

    private fun refreshDiscs() {
        val d = disk ?: return
        runCatching {
            val n = d.count()
            _discs.value = if (n > 1) DiscState(n, d.index(), (0 until n).map { d.label(it) }) else null
        }
    }

    fun swapDisc(index: Int) = post {
        val d = disk ?: return@post
        if (d.swap(index)) { refreshDiscs(); messages.tryEmit("Inserted ${_discs.value?.labels?.getOrNull(index) ?: "disc ${index + 1}"}") }
        else messages.tryEmit("Couldn't change disc")
    }
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    private val rewindBuffer = ArrayDeque<ByteArray>()
    private var rewindCap = 0
    private val frameLock = Any()
    private var back = ByteArray(0)
    private var front = ByteArray(0)
    @Volatile private var running = true
    private var thread: Thread? = null

    private val saveName = gameId
    private val sramFile = File(AppPaths.saves, "$saveName.srm")
    private fun stateFile(slot: Int) = File(AppPaths.states, "$saveName.slot$slot.state")
    private fun thumbFile(slot: Int) = File(AppPaths.states, "$saveName.slot$slot.png")

    // C strings handed to the core must outlive the call.
    private val cstrings = HashMap<String, MemorySegment>()
    private fun cstr(s: String): MemorySegment = cstrings.getOrPut(s) { core.arena.allocateFrom(s) }

    fun start() {
        thread = Thread({ runLoop() }, "libretro-${coreFile.nameWithoutExtension}").apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    fun post(cmd: () -> Unit) { commands += cmd }

    fun saveState(slot: Int) = post {
        val data = core.serialize() ?: run { messages.tryEmit("This core doesn't support save states"); return@post }
        stateFile(slot).writeBytes(data)
        frame?.let { f -> encodePng(f)?.let { thumbFile(slot).writeBytes(it) } }
        messages.tryEmit(if (slot == 0) "Auto-saved" else "Saved to slot $slot")
    }

    fun loadState(slot: Int) = post {
        val f = stateFile(slot)
        if (!f.isFile) { messages.tryEmit("Slot $slot is empty"); return@post }
        if (core.unserialize(f.readBytes())) messages.tryEmit("Loaded slot $slot") else messages.tryEmit("Couldn't load state")
    }

    fun reset() = post { core.reset(); messages.tryEmit("Reset") }

    fun slots(): List<SaveSlot> = (0..4).map { SaveSlot(it, stateFile(it), thumbFile(it), stateFile(it).lastModified()) }

    private fun runLoop() {
        try {
            core = LibretroCore(coreFile.toPath(), this)
            if (core.apiVersion() != Retro.API_VERSION) error("Unsupported libretro API version")
            core.init()
            val sys = core.systemInfo()
            val (path, data) = prepareContent(sys)
            if (!core.loadGame(path, data)) error("${sys.libraryName} couldn't load this game")
            av = core.avInfo().also { applyAv(it) }
            startHwRender(av!!)
            refreshDiscs()
            (0 until 4).forEach { core.setPortDevice(it, Retro.DEVICE_JOYPAD) }
            loadSram()
            openAudio(av!!.sampleRate)
            core.serializeSize().takeIf { rewindEnabled && it in 1..(16L shl 20) }?.let { rewindCap = ((256L shl 20) / it).toInt().coerceIn(60, 1800) }
            _status.value = Status.RUNNING

            var next = System.nanoTime()
            var lastSramSave = System.currentTimeMillis()
            var frameNo = 0L
            while (running) {
                while (true) { val c = commands.poll() ?: break; runCatching(c).onFailure { it.printStackTrace() } }
                if (paused) { _status.value = Status.PAUSED; Thread.sleep(16); next = System.nanoTime(); continue }
                _status.value = Status.RUNNING

                val fps = av?.fps?.takeIf { it in 1.0..240.0 } ?: 60.0
                val frameNs = (1_000_000_000.0 / fps).toLong()

                if (rewinding && rewindBuffer.isNotEmpty()) {
                    core.unserialize(rewindBuffer.pollLast()); core.run()
                } else {
                    val runs = if (fastForward) 4 else 1
                    repeat(runs) { i -> muteAudio = i > 0; core.run() }
                    muteAudio = false
                    if (rewindCap > 0 && frameNo % 2 == 0L) {
                        core.serialize()?.let { rewindBuffer.addLast(it); if (rewindBuffer.size > rewindCap) rewindBuffer.pollFirst() }
                    }
                }
                flushSingleSamples()
                frameNo++
                // Resume after one frame (some cores reject a state before they've run once).
                if (frameNo == 1L && resumeAutoSave && stateFile(0).isFile) {
                    if (core.unserialize(stateFile(0).readBytes())) messages.tryEmit("Resumed where you left off")
                }
                if (System.currentTimeMillis() - lastSramSave > 30_000) { saveSram(); lastSramSave = System.currentTimeMillis() }

                next += frameNs
                val sleep = next - System.nanoTime()
                if (sleep > 0) Thread.sleep(sleep / 1_000_000, (sleep % 1_000_000).toInt())
                else if (sleep < -frameNs * 5) next = System.nanoTime()
            }
            // Drain pending commands (e.g. the auto-save requested on exit).
            while (true) { val c = commands.poll() ?: break; runCatching(c).onFailure { it.printStackTrace() } }
            saveSram()
            stopHwRender()
            core.unloadGame()
            core.deinit()
            _status.value = Status.STOPPED
        } catch (t: Throwable) {
            t.printStackTrace()
            error.value = t.message ?: t.toString()
            _status.value = Status.ERROR
        } finally {
            audio?.runCatching { stop(); close() }
            if (::core.isInitialized) core.close()
        }
    }

    /** Extracts single-ROM zips for cores that can't read archives. */
    private fun prepareContent(sys: SystemInfo): Pair<String?, ByteArray?> {
        if (romPath == null) return null to null
        var file = File(romPath)
        val exts = sys.validExtensions.lowercase().split('|')
        if (file.extension.equals("zip", true) && "zip" !in exts && !sys.blockExtract) {
            ZipFile(file).use { z ->
                val entry = z.entries().asSequence().firstOrNull { e -> !e.isDirectory && e.name.substringAfterLast('.').lowercase() in exts }
                    ?: error("No compatible file inside ${file.name}")
                val out = File(AppPaths.cache, "content/${gameId}/" + File(entry.name).name).apply { parentFile.mkdirs() }
                if (!out.isFile) z.getInputStream(entry).use { i -> out.outputStream().use { i.copyTo(it) } }
                file = out
            }
        }
        val bytes = if (sys.needFullpath) null else file.readBytes()
        return file.path to bytes
    }

    private fun applyAv(a: AvInfo) {
        aspectRatio = if (a.aspectRatio > 0f) a.aspectRatio else a.baseWidth.toFloat() / a.baseHeight.coerceAtLeast(1)
    }

    // ---- SRAM ----
    private fun loadSram() {
        val mem = core.memory(Retro.MEMORY_SAVE_RAM) ?: return
        if (sramFile.isFile) {
            val bytes = sramFile.readBytes()
            MemorySegment.copy(bytes, 0, mem, ValueLayout.JAVA_BYTE, 0, minOf(bytes.size.toLong(), mem.byteSize()).toInt())
        }
    }

    private fun saveSram() {
        val mem = core.memory(Retro.MEMORY_SAVE_RAM) ?: return
        runCatching { sramFile.writeBytes(mem.toArray(ValueLayout.JAVA_BYTE)) }
    }

    // ---- Audio ----
    @Volatile private var muteAudio = false

    private fun openAudio(rate: Double) {
        runCatching {
            val fmt = AudioFormat(rate.toFloat(), 16, 2, true, false)
            audio = AudioSystem.getSourceDataLine(fmt).apply {
                open(fmt, ((rate * 4) * 0.08).toInt() / 4 * 4)
                start()
            }
        }.onFailure { System.err.println("Audio unavailable: ${it.message}") }
    }

    override fun audioSample(left: Short, right: Short) {
        if (singleLen + 4 > singleSamples.size) flushSingleSamples()
        singleSamples[singleLen++] = left.toByte(); singleSamples[singleLen++] = (left.toInt() shr 8).toByte()
        singleSamples[singleLen++] = right.toByte(); singleSamples[singleLen++] = (right.toInt() shr 8).toByte()
    }

    private fun flushSingleSamples() {
        if (singleLen == 0) return
        writeAudio(singleSamples, singleLen); singleLen = 0
    }

    override fun audioSampleBatch(data: MemorySegment, frames: Long): Long {
        val bytes = (frames * 4).toInt().coerceAtMost(audioBuf.size)
        MemorySegment.copy(data.reinterpret(frames * 4), ValueLayout.JAVA_BYTE, 0, audioBuf, 0, bytes)
        writeAudio(audioBuf, bytes)
        return frames
    }

    private fun writeAudio(buf: ByteArray, len: Int) {
        val line = audio ?: return
        if (muteAudio || rewinding) return
        val n = minOf(len, line.available()) / 4 * 4
        if (n > 0) line.write(buf, 0, n)
    }

    // ---- Video ----
    override fun videoRefresh(data: MemorySegment, width: Int, height: Int, pitch: Long) {
        if (data.address() == HwRenderRequest.FRAME_BUFFER_VALID) { hwFrame(width, height); return }
        if (data.address() == 0L || width <= 0 || height <= 0) return  // duped frame
        val size = width * height * 4
        if (back.size != size) back = ByteArray(size)
        val src = data.reinterpret(pitch * height)
        val out = back
        when (pixelFormat) {
            Retro.PIXEL_XRGB8888 -> {
                for (y in 0 until height) MemorySegment.copy(src, ValueLayout.JAVA_BYTE, y * pitch, out, y * width * 4, width * 4)
                var i = 3; while (i < size) { out[i] = -1; i += 4 }
            }
            else -> {
                val rgb565 = pixelFormat == Retro.PIXEL_RGB565
                var o = 0
                for (y in 0 until height) {
                    val row = y * pitch
                    for (x in 0 until width) {
                        val p = src.get(ValueLayout.JAVA_SHORT_UNALIGNED, row + x * 2).toInt() and 0xffff
                        val r: Int; val g: Int; val b: Int
                        if (rgb565) { r = (p shr 11) and 0x1f; g = (p shr 5) and 0x3f; b = p and 0x1f
                            out[o] = ((b shl 3) or (b shr 2)).toByte(); out[o + 1] = ((g shl 2) or (g shr 4)).toByte(); out[o + 2] = ((r shl 3) or (r shr 2)).toByte()
                        } else { r = (p shr 10) and 0x1f; g = (p shr 5) and 0x1f; b = p and 0x1f
                            out[o] = ((b shl 3) or (b shr 2)).toByte(); out[o + 1] = ((g shl 3) or (g shr 2)).toByte(); out[o + 2] = ((r shl 3) or (r shr 2)).toByte()
                        }
                        out[o + 3] = -1; o += 4
                    }
                }
            }
        }
        synchronized(frameLock) {
            val t = front; front = back; back = t
            frame = Frame(front, width, height)
            frameCounter++
        }
    }

    /** A hardware-rendered frame is ready in the framebuffer: read it back into the normal pipeline. */
    private fun hwFrame(width: Int, height: Int) {
        val r = gl ?: return
        val req = hwRequest ?: return
        val w = width.coerceIn(1, r.width); val h = height.coerceIn(1, r.height)
        if (back.size != w * h * 4) back = ByteArray(w * h * 4)
        r.readFrame(w, h, req.bottomLeftOrigin, back)
        synchronized(frameLock) {
            val t = front; front = back; back = t
            frame = Frame(front, w, h)
            frameCounter++
        }
    }

    /** Builds a Skia image of the latest frame (call from UI thread). */
    fun snapshot(): Image? = synchronized(frameLock) {
        val f = frame ?: return null
        Image.makeRaster(ImageInfo(f.width, f.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), f.pixels, f.width * 4)
    }

    private fun encodePng(f: Frame): ByteArray? = runCatching {
        synchronized(frameLock) { Image.makeRaster(ImageInfo(f.width, f.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), f.pixels, f.width * 4) }
            .encodeToData(EncodedImageFormat.PNG)?.bytes
    }.getOrNull()

    // ---- Input ----
    private val padBits = IntArray(4)

    override fun inputPoll() { for (p in 0 until 4) padBits[p] = controllers.portButtons(p) }

    /** RetroPad id -> positional pad button (SNES layout: B=south, A=east, Y=west, X=north). */
    @Volatile private var retroMap: Array<Pad> = inputMap.toTypedArray()

    /** Applies a new mapping immediately (from the in-game Controls panel). */
    fun setInputMap(map: List<Pad>) { retroMap = map.toTypedArray() }


    override fun inputState(port: Int, device: Int, index: Int, id: Int): Short {
        if (port !in 0..3) return 0
        val bits = padBits[port]
        return when (device and 0xff) {
            Retro.DEVICE_JOYPAD -> if (id == Retro.DEVICE_ID_JOYPAD_MASK) {
                var m = 0; retroMap.forEachIndexed { i, p -> if (bits and (1 shl p.ordinal) != 0) m = m or (1 shl i) }; m.toShort()
            } else retroMap.getOrNull(id)?.let { if (bits and (1 shl it.ordinal) != 0) 1 else 0 }?.toShort() ?: 0
            Retro.DEVICE_ANALOG -> {
                val s = controllers.ports[port]
                when (index) { 0 -> if (id == 0) s.lx else s.ly; 1 -> if (id == 0) s.rx else s.ry; else -> 0 }
            }
            else -> 0
        }
    }

    override fun rumble(port: Int, effect: Int, strength: Short): Boolean {
        val v = strength.toInt() and 0xffff
        controllers.rumble(port, if (effect == 0) v else 0, if (effect == 1) v else 0, 200)
        return true
    }

    // ---- Environment ----
    override fun environment(cmd: Int, data: MemorySegment): Boolean {
        val c = cmd and Retro.EXPERIMENTAL.inv()
        val d = data.reinterpret(64)
        return when (c) {
            Retro.ENV_GET_CAN_DUPE -> { d.set(ValueLayout.JAVA_BOOLEAN, 0, true); true }
            Retro.ENV_GET_OVERSCAN -> { d.set(ValueLayout.JAVA_BOOLEAN, 0, false); true }
            Retro.ENV_SET_PIXEL_FORMAT -> {
                val f = d.get(ValueLayout.JAVA_INT, 0)
                if (f in 0..2) { pixelFormat = f; true } else false
            }
            Retro.ENV_GET_SYSTEM_DIRECTORY -> { d.set(ValueLayout.ADDRESS, 0, cstr(systemDir.path)); true }
            Retro.ENV_GET_SAVE_DIRECTORY -> { d.set(ValueLayout.ADDRESS, 0, cstr(AppPaths.saves.path)); true }
            Retro.ENV_GET_CORE_ASSETS_DIRECTORY -> { d.set(ValueLayout.ADDRESS, 0, cstr(systemDir.path)); true }
            Retro.ENV_GET_LIBRETRO_PATH -> { d.set(ValueLayout.ADDRESS, 0, cstr(coreFile.path)); true }
            Retro.ENV_GET_USERNAME -> { d.set(ValueLayout.ADDRESS, 0, cstr(System.getProperty("user.name") ?: "Player")); true }
            Retro.ENV_GET_LANGUAGE -> { d.set(ValueLayout.JAVA_INT, 0, 0); true }
            Retro.ENV_SET_VARIABLES -> { registerOptions(CoreOptionParser.legacy(data)); true }
            Retro.ENV_SET_CORE_OPTIONS -> { registerOptions(CoreOptionParser.v1(data)); true }
            Retro.ENV_SET_CORE_OPTIONS_INTL -> { registerOptions(CoreOptionParser.v1(CoreOptionParser.intlUs(data))); true }
            Retro.ENV_SET_CORE_OPTIONS_V2 -> { registerOptions(CoreOptionParser.v2(data)); true }
            Retro.ENV_SET_CORE_OPTIONS_V2_INTL -> { registerOptions(CoreOptionParser.v2(CoreOptionParser.intlUs(data))); true }
            Retro.ENV_SET_CORE_OPTIONS_DISPLAY -> {
                val key = LibretroCore.cstr(d.get(ValueLayout.ADDRESS, 0))
                val visible = d.get(ValueLayout.JAVA_BOOLEAN, 8)
                if (key != null) _options.value = _options.value.map { if (it.key == key) it.copy(visible = visible) else it }
                true
            }
            Retro.ENV_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK -> true
            Retro.ENV_SET_VARIABLE -> {
                if (data.address() == 0L) return true  // capability query
                val key = LibretroCore.cstr(d.get(ValueLayout.ADDRESS, 0)) ?: return false
                val value = LibretroCore.cstr(d.get(ValueLayout.ADDRESS, 8)) ?: return false
                variables[key] = value; _optionValues.value = HashMap(variables); true
            }
            Retro.ENV_SET_DISK_CONTROL_INTERFACE -> { disk = DiskControl(data, ext = false); true }
            Retro.ENV_SET_DISK_CONTROL_EXT_INTERFACE -> { disk = DiskControl(data, ext = true); true }
            Retro.ENV_GET_DISK_CONTROL_INTERFACE_VERSION -> { d.set(ValueLayout.JAVA_INT, 0, 1); true }
            Retro.ENV_GET_VARIABLE -> {
                val key = LibretroCore.cstr(d.get(ValueLayout.ADDRESS, 0)) ?: return false
                val v = variables[key] ?: return false
                d.set(ValueLayout.ADDRESS, 8, cstr(v)); true
            }
            Retro.ENV_GET_VARIABLE_UPDATE -> { d.set(ValueLayout.JAVA_BOOLEAN, 0, variablesDirty); variablesDirty = false; true }
            Retro.ENV_GET_CORE_OPTIONS_VERSION -> { d.set(ValueLayout.JAVA_INT, 0, 2); true }
            Retro.ENV_GET_INPUT_BITMASKS -> true
            Retro.ENV_GET_AUDIO_VIDEO_ENABLE -> { d.set(ValueLayout.JAVA_INT, 0, 3); true }
            Retro.ENV_GET_FASTFORWARDING -> { d.set(ValueLayout.JAVA_BOOLEAN, 0, fastForward); true }
            Retro.ENV_GET_RUMBLE_INTERFACE -> { d.set(ValueLayout.ADDRESS, 0, core.rumbleStub); true }
            Retro.ENV_SET_GEOMETRY -> {
                val w = d.get(ValueLayout.JAVA_INT, 0); val h = d.get(ValueLayout.JAVA_INT, 4); val ar = d.get(ValueLayout.JAVA_FLOAT, 16)
                aspectRatio = if (ar > 0f) ar else w.toFloat() / h.coerceAtLeast(1); true
            }
            Retro.ENV_SET_SYSTEM_AV_INFO -> {
                val a = LibretroCore.readAv(data.reinterpret(40)); av = a; applyAv(a)
                gl?.let { r -> hwRequest?.let { q -> r.ensureFramebuffer(maxOf(r.width, a.maxWidth), maxOf(r.height, a.maxHeight), q.depth, q.stencil) } }
                audio?.runCatching { stop(); close() }; openAudio(a.sampleRate); true
            }
            Retro.ENV_SET_MESSAGE -> { LibretroCore.cstr(d.get(ValueLayout.ADDRESS, 0))?.let { messages.tryEmit(it) }; true }
            Retro.ENV_SHUTDOWN -> { running = false; true }
            // OpenGL / OpenGL core only (no GLES, Vulkan or D3D). The GL context is created after load_game.
            Retro.ENV_SET_HW_RENDER -> HwRenderRequest.accept(data, core.hwFramebufferStub, core.hwProcAddressStub)?.also { hwRequest = it } != null
            Retro.ENV_SET_PERFORMANCE_LEVEL, Retro.ENV_SET_INPUT_DESCRIPTORS, Retro.ENV_SET_SUPPORT_NO_GAME,
            Retro.ENV_SET_CONTROLLER_INFO, Retro.ENV_SET_SUBSYSTEM_INFO, Retro.ENV_SET_MEMORY_MAPS,
            Retro.ENV_SET_SUPPORT_ACHIEVEMENTS, Retro.ENV_SET_SERIALIZATION_QUIRKS, Retro.ENV_SET_ROTATION,
            Retro.ENV_SET_CONTENT_INFO_OVERRIDE -> true
            else -> false
        }
    }
}
