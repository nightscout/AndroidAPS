package app.aaps.wear.complications.cwf

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules the secondless picture keeps.
 *
 * It is what the always-on slot publishes, so that the runtime can swap it in the moment the watch
 * dozes instead of us trying - and failing - to deliver one in time.
 *
 * Held pictures can go wrong in a way freshly drawn ones cannot, and the minute is where that shows:
 * the seconds are absent, so nobody sees those go stale, but the minute is drawn both as a hand and
 * as text.
 */
class CwfMinuteFrameTest {

    private fun frame(marker: Int) = byteArrayOf(marker.toByte())

    /** Deliberately mid-minute, so a test can move inside it or out of it on purpose. */
    private val midMinute = 600_000L + 20_000L

    @Test
    fun `hands over a picture drawn in this minute`() {
        val minute = CwfMinuteFrame()
        minute.offer(midMinute, frame(1))

        assertThat(minute.take(midMinute + 8_000)?.bytes).isEqualTo(frame(1))
    }

    @Test
    fun `refuses a picture from the previous minute`() {
        // The one fault a held picture can carry that a fresh one cannot, and the fault that took
        // longest to find on this watch face: a minute hand showing the minute before
        val minute = CwfMinuteFrame()
        minute.offer(second = 659_000, bytes = frame(1))

        assertThat(minute.take(now = 660_500)).isNull()
    }

    @Test
    fun `refuses a picture drawn for a moment still to come`() {
        val minute = CwfMinuteFrame()
        minute.offer(midMinute + 2_000, frame(1))

        assertThat(minute.take(midMinute)).isNull()
    }

    @Test
    fun `has nothing to hand over before anything is drawn`() {
        val minute = CwfMinuteFrame()

        assertThat(minute.take(midMinute)).isNull()
        assertThat(minute.prepared).isFalse()
    }

    @Test
    fun `asks for a picture when it holds none`() {
        assertThat(CwfMinuteFrame().stale(midMinute)).isTrue()
    }

    @Test
    fun `asks for a new picture as soon as the minute turns`() {
        val minute = CwfMinuteFrame()
        minute.offer(second = 659_800, bytes = frame(1))

        assertThat(minute.stale(now = 660_100)).isTrue()
    }

    @Test
    fun `asks for nothing while the minute has not changed`() {
        // This is what keeps the cost at one compression a minute rather than one a second. Without
        // seconds nothing else about the picture moves that the eye can see - the minute hand travels
        // a tenth of a degree per second.
        val minute = CwfMinuteFrame()
        minute.offer(midMinute, frame(1))

        assertThat(minute.stale(midMinute + 30_000)).isFalse()
    }

    @Test
    fun `drops what it holds when the data changes`() {
        val minute = CwfMinuteFrame()
        minute.offer(midMinute, frame(1))

        minute.clear()

        assertThat(minute.take(midMinute)).isNull()
        assertThat(minute.stale(midMinute)).isTrue()
    }
}
