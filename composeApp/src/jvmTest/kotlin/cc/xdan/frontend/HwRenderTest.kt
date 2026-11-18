package cc.xdan.frontend

import cc.xdan.frontend.libretro.GlRenderer
import cc.xdan.frontend.libretro.HwRenderRequest
import org.lwjgl.opengl.GL11
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HwRenderTest {
    /** retro_hw_render_callback as a core fills it in; we must read it and write our two callbacks back. */
    @Test fun parsesHwRenderRequest() = Arena.ofConfined().use { a ->
        val s = a.allocate(64, 8)
        s.set(ValueLayout.JAVA_INT, 0, HwRenderRequest.CONTEXT_OPENGL_CORE)
        s.set(ValueLayout.ADDRESS, 8, MemorySegment.ofAddress(0x1234))          // context_reset
        s.set(ValueLayout.JAVA_BOOLEAN, 32, true); s.set(ValueLayout.JAVA_BOOLEAN, 33, true); s.set(ValueLayout.JAVA_BOOLEAN, 34, true)
        s.set(ValueLayout.JAVA_INT, 36, 3); s.set(ValueLayout.JAVA_INT, 40, 3)
        s.set(ValueLayout.ADDRESS, 48, MemorySegment.ofAddress(0x5678))         // context_destroy
        val req = assertNotNull(HwRenderRequest.accept(s, MemorySegment.ofAddress(0xAAAA), MemorySegment.ofAddress(0xBBBB)))
        assertEquals(0x1234, req.contextReset.address()); assertEquals(0x5678, req.contextDestroy.address())
        assertEquals(true, req.coreProfile); assertEquals(true, req.bottomLeftOrigin); assertEquals(3, req.versionMajor)
        assertEquals(0xAAAA, s.get(ValueLayout.ADDRESS, 16).address(), "get_current_framebuffer written")
        assertEquals(0xBBBB, s.get(ValueLayout.ADDRESS, 24).address(), "get_proc_address written")
        // Vulkan (6) isn't supported: refuse so the core can fall back.
        s.set(ValueLayout.JAVA_INT, 0, 6)
        assertNull(HwRenderRequest.accept(s, MemorySegment.NULL, MemorySegment.NULL))
    }

    /** Real off-screen GL: draw known colours, read back through the frame path. Skipped where no GL context exists (headless CI). */
    @Test fun rendersAndReadsBackOffscreen() {
        val r = runCatching { GlRenderer.create(core = true) }.onFailure { println("GL: no context here (${it.message}) — skipping") }.getOrNull() ?: return
        println("GL: context created, ${org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)} / ${org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)}")
        try {
            r.ensureFramebuffer(64, 32, depth = true, stencil = true)
            kotlin.test.assertTrue(r.procAddress("glClear") != 0L, "GL functions resolve for the core")
            GL11.glClearColor(1f, 0f, 0f, 1f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)       // red everywhere
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(0, 16, 64, 16)               // GL top half (y 16..31)…
            GL11.glClearColor(0f, 1f, 0f, 1f); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)       // …green
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glFinish()
            val out = ByteArray(64 * 32 * 4)
            r.readFrame(64, 32, bottomLeftOrigin = true, out)
            fun px(x: Int, y: Int) = (0..3).map { out[(y * 64 + x) * 4 + it].toInt() and 0xff }   // BGRA
            assertEquals(listOf(0, 255, 0, 255), px(10, 2), "top row is green (flipped from GL's bottom-left origin)")
            assertEquals(listOf(0, 0, 255, 255), px(10, 30), "bottom row is red")
        } finally { r.destroy() }
    }
}
