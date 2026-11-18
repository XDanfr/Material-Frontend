package cc.xdan.frontend.libretro

import cc.xdan.frontend.data.HostOs
import org.lwjgl.BufferUtils
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.CGL
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer

/**
 * What a core asked for with RETRO_ENVIRONMENT_SET_HW_RENDER
 * (struct retro_hw_render_callback, 64-bit layout from libretro.h).
 */
class HwRenderRequest(
    val contextType: Int,
    val contextReset: MemorySegment,
    val contextDestroy: MemorySegment,
    val depth: Boolean,
    val stencil: Boolean,
    val bottomLeftOrigin: Boolean,
    val versionMajor: Int,
    val versionMinor: Int,
) {
    val coreProfile get() = contextType == CONTEXT_OPENGL_CORE

    companion object {
        const val CONTEXT_OPENGL = 1
        const val CONTEXT_OPENGL_CORE = 3
        /** RETRO_HW_FRAME_BUFFER_VALID = ((void*)-1) */
        const val FRAME_BUFFER_VALID = -1L

        /** Reads the request and writes our two callbacks into it. Returns null for unsupported types (GLES, Vulkan, D3D). */
        fun accept(struct: MemorySegment, getFramebuffer: MemorySegment, getProcAddress: MemorySegment): HwRenderRequest? {
            val s = struct.reinterpret(64)
            val type = s.get(ValueLayout.JAVA_INT, 0)
            if (type != CONTEXT_OPENGL && type != CONTEXT_OPENGL_CORE) return null
            s.set(ValueLayout.ADDRESS, 16, getFramebuffer)
            s.set(ValueLayout.ADDRESS, 24, getProcAddress)
            return HwRenderRequest(
                type, s.get(ValueLayout.ADDRESS, 8), s.get(ValueLayout.ADDRESS, 48),
                s.get(ValueLayout.JAVA_BOOLEAN, 32), s.get(ValueLayout.JAVA_BOOLEAN, 33), s.get(ValueLayout.JAVA_BOOLEAN, 34),
                s.get(ValueLayout.JAVA_INT, 36), s.get(ValueLayout.JAVA_INT, 40),
            )
        }
    }
}

/**
 * An off-screen OpenGL context with a framebuffer the core renders into; frames are read back
 * into the same BGRA pipeline software cores use. Create, use and destroy on one thread.
 * macOS: CGL (works off the main thread). Windows/Linux: a hidden GLFW window.
 */
class GlRenderer private constructor(private val destroyContext: () -> Unit) {
    var fbo = 0; private set
    private var color = 0
    private var depthStencil = 0
    var width = 0; private set
    var height = 0; private set
    private var readBuf: ByteBuffer = BufferUtils.createByteBuffer(4)

    /** Address of a GL function for the core (retro_hw_get_proc_address_t). */
    fun procAddress(name: String): Long = runCatching { GL.getFunctionProvider()?.getFunctionAddress(name) ?: 0L }.getOrDefault(0L)

    /** (Re)creates the framebuffer at the core's maximum size. */
    fun ensureFramebuffer(w: Int, h: Int, depth: Boolean, stencil: Boolean) {
        if (w == width && h == height && fbo != 0) return
        deleteFramebuffer()
        width = w.coerceAtLeast(1); height = h.coerceAtLeast(1)
        fbo = GL30.glGenFramebuffers()
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo)
        color = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, null as ByteBuffer?)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0)
        if (depth || stencil) {
            depthStencil = GL30.glGenRenderbuffers()
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencil)
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, if (stencil) GL30.GL_DEPTH24_STENCIL8 else GL11.GL_DEPTH_COMPONENT, width, height)
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, if (stencil) GL30.GL_DEPTH_STENCIL_ATTACHMENT else GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, depthStencil)
        }
        val status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
        check(status == GL30.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete (0x${status.toString(16)})" }
        GL11.glViewport(0, 0, width, height)
        GL11.glClearColor(0f, 0f, 0f, 1f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        readBuf = BufferUtils.createByteBuffer(width * height * 4)
    }

    /** Reads the frame the core just drew (w×h from the top-left of the framebuffer) into [out] as BGRA, top row first. */
    fun readFrame(w: Int, h: Int, bottomLeftOrigin: Boolean, out: ByteArray) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4)
        readBuf.clear()
        // GL's origin is bottom-left: a core that renders "top-left origin" content sits at the top of the FBO.
        val y = if (bottomLeftOrigin) 0 else height - h
        GL11.glReadPixels(0, y, w, h, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, readBuf)
        val row = w * 4
        for (r in 0 until h) {
            val src = if (bottomLeftOrigin) (h - 1 - r) * row else r * row
            readBuf.position(src); readBuf.get(out, r * row, row)
        }
        var i = 3; while (i < w * h * 4) { out[i] = -1; i += 4 }
    }

    private fun deleteFramebuffer() {
        if (fbo != 0) GL30.glDeleteFramebuffers(fbo)
        if (color != 0) GL11.glDeleteTextures(color)
        if (depthStencil != 0) GL30.glDeleteRenderbuffers(depthStencil)
        fbo = 0; color = 0; depthStencil = 0
    }

    fun destroy() { runCatching { deleteFramebuffer() }; destroyContext() }

    companion object {
        /** Creates and makes current an off-screen context. [core] = 3.2+ core profile, else legacy/compatibility. */
        fun create(core: Boolean): GlRenderer = if (HostOs.current == HostOs.MAC) createCgl(core) else createGlfw(core)

        private fun createCgl(core: Boolean): GlRenderer {
            val attrs = intArrayOf(
                CGL.kCGLPFAOpenGLProfile, if (core) CGL.kCGLOGLPVersion_3_2_Core else CGL.kCGLOGLPVersion_Legacy,
                CGL.kCGLPFAColorSize, 24, CGL.kCGLPFADepthSize, 24, CGL.kCGLPFAStencilSize, 8, CGL.kCGLPFAAccelerated, 0,
            )
            val pix = PointerBuffer.allocateDirect(1)
            val n = intArrayOf(0)
            check(CGL.CGLChoosePixelFormat(attrs, pix, n) == 0 && pix.get(0) != 0L) { "No suitable OpenGL pixel format" }
            val ctx = PointerBuffer.allocateDirect(1)
            check(CGL.CGLCreateContext(pix.get(0), 0L, ctx) == 0 && ctx.get(0) != 0L) { "Couldn't create an OpenGL context" }
            CGL.CGLDestroyPixelFormat(pix.get(0))
            val context = ctx.get(0)
            CGL.CGLSetCurrentContext(context)
            GL.createCapabilities()
            return GlRenderer { CGL.CGLSetCurrentContext(0L); CGL.CGLDestroyContext(context) }
        }

        private fun createGlfw(core: Boolean): GlRenderer {
            check(GLFW.glfwInit()) { "Couldn't initialise GLFW" }
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            if (core) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            }
            val window = GLFW.glfwCreateWindow(16, 16, "Material Frontend GL", 0L, 0L)
            check(window != 0L) { "Couldn't create an OpenGL context" }
            GLFW.glfwMakeContextCurrent(window)
            GL.createCapabilities()
            return GlRenderer { GLFW.glfwMakeContextCurrent(0L); GLFW.glfwDestroyWindow(window) }
        }
    }
}
