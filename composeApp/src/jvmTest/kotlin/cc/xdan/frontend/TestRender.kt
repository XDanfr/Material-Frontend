package cc.xdan.frontend

import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * The scenes' coroutine dispatcher in tests: work is queued and run on the test thread between frames, like a
 * window's UI thread. (With the default Unconfined dispatcher, code resuming after a delay ran on a timer
 * thread and changed UI state mid-frame, corrupting Compose's layout bookkeeping.)
 */
object TestFrames : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
    fun drain() { var n = 0; while (n++ < 10_000) (queue.poll() ?: return).run() }
}

/** Runs queued UI work, then renders a frame; still skips Compose's rare one-frame RectList glitch (the app does too). */
fun ImageComposeScene.renderTolerant(nanoTime: Long = 0): Image {
    TestFrames.drain()
    repeat(3) { attempt ->
        try { return render(nanoTime + attempt).also { TestFrames.drain() } } catch (e: IllegalArgumentException) { if (!isComposeRectGlitch(e)) throw e }
    }
    return render(nanoTime + 3)
}
