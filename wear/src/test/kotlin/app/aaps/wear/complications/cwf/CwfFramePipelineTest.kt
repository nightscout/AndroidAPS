package app.aaps.wear.complications.cwf

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.watchfaces.CustomWatchface
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The order in which a frame is prepared.
 *
 * Every test here is a fault that reached a wrist. Each call the pipeline makes was right on its
 * own; the order was the part that broke, and nothing could see it - the steps were spread through
 * one long function and no test could observe them. Now the pipeline asks a [CwfRenderTarget], and a
 * fake one writes down what it was asked and when.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class CwfFramePipelineTest {

    /** Writes down every call, in order, and draws a blank layer for each one asked for. */
    private open class RecordingTarget(override val styleId: Int = 1) : CwfRenderTarget {

        val calls = mutableListOf<String>()
        var laidOut = false

        override fun prepareLayout(width: Int, height: Int) {
            laidOut = true
            calls += "prepareLayout"
        }

        override fun setRenderInstant(millis: Long?) {
            calls += if (millis == null) "clearInstant" else "setInstant"
        }

        override fun setRenderAmbient(ambient: Boolean) {
            calls += "setAmbient($ambient)"
        }

        override fun setRenderSeconds(show: Boolean?) {
            calls += if (show == null) "clearSeconds" else "setSeconds($show)"
        }

        override fun refreshRenderData() {
            calls += "refreshData"
        }

        override fun updateSecondVisibility() {
            calls += "secondVisibility"
        }

        override fun setSecond() {
            calls += "moveClock"
        }

        override fun renderLayer(width: Int, height: Int, layer: CustomWatchface.RenderLayer): Bitmap {
            // The fault this catches: a frame that reached the views before they existed raised
            // UninitializedPropertyAccessException, and nine frames were lost in thirty seconds
            check(laidOut) { "renderLayer before prepareLayout" }
            calls += "draw:${layer.name}"
            return createBitmap(width, height)
        }
    }

    private fun pipeline(target: CwfRenderTarget) =
        CwfFramePipeline(RuntimeEnvironment.getApplication(), AAPSLoggerTest()) { target }

    private fun compose(target: RecordingTarget, instant: Long = 60_000, ambient: Boolean = false, dozedAtDraw: Boolean = false) =
        pipeline(target).compose(instant, ambient, 16, 16) { dozedAtDraw }.recycle()

    private fun List<String>.before(first: String, second: String): Boolean =
        indexOf(first) in 0..<indexOf(second) && contains(second)

    @Test
    fun `lays the views out before anything draws`() {
        val target = RecordingTarget()

        compose(target)

        // RecordingTarget would have thrown, but say it as an assertion too so the reason is written
        assertThat(target.calls.first()).isEqualTo("prepareLayout")
    }

    @Test
    fun `moves the clock before the cached layers are drawn`() {
        // The fault: the cached layers are drawn from the same views, so drawing them first froze the
        // hour and minute hands at their previous positions for a whole minute, while the second
        // hand - drawn live afterwards - stayed right
        val target = RecordingTarget()

        compose(target)

        assertThat(target.calls.before("moveClock", "draw:MIDDLE")).isTrue()
        assertThat(target.calls.before("moveClock", "draw:HANDS")).isTrue()
    }

    @Test
    fun `moves the clock on every frame, not only when the data is reloaded`() {
        // The fault: the clock only ever moved as a side effect of the data reload, which most frames
        // skip - so the watch face showed a time that never advanced
        val target = RecordingTarget()
        val pipeline = pipeline(target)
        pipeline.compose(60_000, false, 16, 16).recycle()
        target.calls.clear()

        pipeline.compose(61_000, false, 16, 16).recycle()

        assertThat(target.calls).contains("moveClock")
        assertThat(target.calls).doesNotContain("refreshData")
    }

    @Test
    fun `decides the seconds before the clock is moved, and again if the watch dozed`() {
        val target = RecordingTarget()

        compose(target, dozedAtDraw = true)

        assertThat(target.calls.before("secondVisibility", "moveClock")).isTrue()
        // Taken back at the end, when the expensive work is done and the two layers that carry the
        // seconds have still not been drawn
        assertThat(target.calls.before("setSeconds(false)", "draw:SECOND_HAND")).isTrue()
        assertThat(target.calls.count { it == "moveClock" }).isEqualTo(2)
    }

    @Test
    fun `leaves the seconds alone when the watch stayed awake`() {
        val target = RecordingTarget()

        compose(target, dozedAtDraw = false)

        assertThat(target.calls).doesNotContain("setSeconds(false)")
    }

    @Test
    fun `never changes the mode inside a frame`() {
        // The mode also chooses the simple always-on display, so a late change would draw an empty
        // picture for anyone using it. Only the seconds may be taken back - see setRenderSeconds.
        val target = RecordingTarget()

        compose(target, ambient = false, dozedAtDraw = true)

        assertThat(target.calls.count { it.startsWith("setAmbient") }).isEqualTo(1)
        assertThat(target.calls).contains("setAmbient(false)")
    }

    @Test
    fun `reads the data on the first frame and not on the next`() {
        // Re-reading costs 77 ms and a clock tick has no reason to pay it: the second changed, not
        // the glucose
        val target = RecordingTarget()
        val pipeline = pipeline(target)

        pipeline.compose(60_000, false, 16, 16).recycle()
        val first = target.calls.count { it == "refreshData" }
        pipeline.compose(60_500, false, 16, 16).recycle()

        assertThat(first).isEqualTo(1)
        assertThat(target.calls.count { it == "refreshData" }).isEqualTo(1)
    }

    @Test
    fun `draws the minute layers again when the minute turns`() {
        val target = RecordingTarget()
        val pipeline = pipeline(target)
        pipeline.compose(60_000, false, 16, 16).recycle()
        target.calls.clear()

        pipeline.compose(120_000, false, 16, 16).recycle()

        assertThat(target.calls).contains("draw:MIDDLE")
        assertThat(target.calls).doesNotContain("refreshData")
    }

    @Test
    fun `puts the clock and the seconds back for whoever draws next`() {
        // The live watch face and the editor share this instance. A frame that kept its instant left
        // them drawing for a moment in the past.
        val target = RecordingTarget()

        compose(target)

        assertThat(target.calls.last()).isEqualTo("clearSeconds")
        assertThat(target.calls).contains("clearInstant")
    }

    @Test
    fun `puts them back even when the frame fails`() {
        val target = object : RecordingTarget() {
            override fun setSecond() {
                super.setSecond()
                error("drawing failed")
            }
        }

        runCatching { compose(target) }

        assertThat(target.calls).contains("clearInstant")
        assertThat(target.calls).contains("clearSeconds")
    }
}
