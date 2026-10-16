package app.aaps.wear.complications.cwf

import app.aaps.wear.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules the prepared-frame queue must keep, written down so a later change cannot quietly break
 * them.
 *
 * Every one of these came from something seen on a watch, not from imagination:
 *
 * - a frame whose second has passed must never be shown - it made the second hand appear to stand
 *   still, then jump;
 * - a frame built for the other mode must never be shown - an ambient frame has no second hand, and
 *   showing one while awake made the hand vanish and come back;
 * - a frame built from older data must never be shown after the data changed, or one reading's
 *   colours would appear beside another reading's value.
 */
class CwfFrameQueueTest {

    private val logger = AAPSLoggerTest()

    private fun queue(token: Int = 1, ambient: Boolean = false) =
        CwfFrameQueue(logger).apply { reset(token, ambient) }

    private fun frame(marker: Int) = byteArrayOf(marker.toByte())

    @Test
    fun `serves the frame prepared for the second being asked for`() {
        val queue = queue()
        queue.offer(second = 10_000, token = 1, forAmbient = false, bytes = frame(1))

        assertThat(queue.take(second = 10_000, token = 1, forAmbient = false)?.bytes).isEqualTo(frame(1))
    }

    @Test
    fun `serves a frame only once`() {
        val queue = queue()
        queue.offer(10_000, 1, false, frame(1))
        queue.take(10_000, 1, false)

        assertThat(queue.take(10_000, 1, false)).isNull()
    }

    @Test
    fun `reports the second a served frame depicts, which is not always the one asked for`() {
        // This is what made the backward jumps possible. The queue answers with the nearest frame
        // within tolerance, so what the wearer sees can be a few hundred milliseconds either side of
        // the second that was requested. The caller has to be told which instant it is really
        // showing, or the promise that time only moves forward guards the wrong number - it did, and
        // the second and minute hands stepped back on a Galaxy Watch 4.
        val queue = queue()
        queue.offer(10_400, 1, false, frame(1))

        val served = queue.take(second = 10_000, token = 1, forAmbient = false)

        assertThat(served?.second).isEqualTo(10_400)
    }

    @Test
    fun `refuses a frame prepared for a second that has passed`() {
        val queue = queue()
        queue.offer(10_000, 1, false, frame(1))

        // A second later. Showing the old frame would put the clock back, which on the watch looked
        // like the second hand freezing and then jumping two seconds.
        assertThat(queue.take(second = 11_000, token = 1, forAmbient = false)).isNull()
    }

    @Test
    fun `refuses a frame prepared for a second still to come`() {
        val queue = queue()
        queue.offer(12_000, 1, false, frame(1))

        assertThat(queue.take(second = 10_000, token = 1, forAmbient = false)).isNull()
    }

    @Test
    fun `refuses every frame built for the other mode`() {
        val queue = queue(ambient = false)
        queue.offer(10_000, 1, forAmbient = false, bytes = frame(1))

        // An ambient frame carries no second hand; showing one on an awake watch is the fault this
        // guards against
        assertThat(queue.take(10_000, token = 1, forAmbient = true)).isNull()
    }

    @Test
    fun `refuses every frame built before the data changed`() {
        val queue = queue(token = 1)
        queue.offer(10_000, token = 1, forAmbient = false, bytes = frame(1))

        assertThat(queue.take(10_000, token = 2, forAmbient = false)).isNull()
    }

    @Test
    fun `discards what it holds when the data changes`() {
        val queue = queue(token = 1)
        queue.offer(10_000, 1, false, frame(1))

        queue.reset(token = 2, forAmbient = false)

        assertThat(queue.size).isEqualTo(0)
    }

    @Test
    fun `discards what it holds when the mode changes`() {
        val queue = queue(ambient = false)
        queue.offer(10_000, 1, false, frame(1))

        queue.reset(token = 1, forAmbient = true)

        assertThat(queue.size).isEqualTo(0)
    }

    @Test
    fun `keeps what it holds when nothing has changed`() {
        val queue = queue(token = 1, ambient = false)
        queue.offer(10_000, 1, false, frame(1))

        queue.reset(token = 1, forAmbient = false)

        assertThat(queue.size).isEqualTo(1)
    }

    @Test
    fun `asks for a whole horizon when it is empty`() {
        val queue = queue()

        assertThat(queue.missing(from = 10_000))
            .containsExactlyElementsIn((0 until CwfFrameQueue.HORIZON_FRAMES).map { 10_000L + it * 1_000 })
            .inOrder()
    }

    @Test
    fun `asks only for the seconds it does not already hold`() {
        val queue = queue()
        queue.offer(11_000, 1, false, frame(1))

        assertThat(queue.missing(from = 10_000)).doesNotContain(11_000L)
    }

    @Test
    fun `continues the series after what is already prepared, not from the present instant`() {
        // A frame takes 200 ms to over a second to build. A producer aiming at "now plus a bit" would
        // keep re-aiming at seconds already gone and the queue would never get ahead - which is the
        // whole reason it exists.
        val queue = queue()
        queue.offer(10_000, 1, false, frame(1))
        queue.offer(11_000, 1, false, frame(2))

        assertThat(queue.missing(from = 10_000).first()).isEqualTo(12_000)
    }

    @Test
    fun `asks for nothing once the horizon is full`() {
        // The regression this guards against: the producer never stopped. It continued the series
        // past the horizon and returned five new future seconds on every call, so the queue drifted
        // into the future, no prepared frame was ever near enough to be used, and the watch spent its
        // time building frames nobody would see - which showed as hands jumping backwards.
        val queue = queue()
        (0 until CwfFrameQueue.HORIZON_FRAMES).forEach { queue.offer(10_000L + it * 1_000, 1, false, frame(it)) }

        assertThat(queue.missing(from = 10_000)).isEmpty()
    }

    @Test
    fun `never asks for a second beyond the horizon`() {
        val queue = queue()
        queue.offer(10_000, 1, false, frame(1))

        val last = 10_000L + (CwfFrameQueue.HORIZON_FRAMES - 1) * 1_000
        assertThat(queue.missing(from = 10_000).all { it <= last }).isTrue()
    }

    @Test
    fun `starts from the given second when nothing is prepared`() {
        assertThat(queue().missing(from = 10_000).first()).isEqualTo(10_000)
    }

    @Test
    fun `forgets frames whose second has passed rather than keeping them for ever`() {
        val queue = queue()
        queue.offer(10_000, 1, false, frame(1))
        queue.offer(11_000, 1, false, frame(2))

        queue.take(second = 11_000, token = 1, forAmbient = false)

        // The 10 s frame can never be shown again, and holding it would only cost memory
        assertThat(queue.size).isEqualTo(0)
    }
}
