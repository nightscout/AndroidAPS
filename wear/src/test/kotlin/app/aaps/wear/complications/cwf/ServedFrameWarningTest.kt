package app.aaps.wear.complications.cwf

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * When a served frame is worth a line in the log.
 *
 * The rule exists so the watch face can stop writing one entry per second while still saying
 * something when the clock misbehaves - which it has, twice, in ways that took days to find.
 */
class ServedFrameWarningTest {

    @Test
    fun `says nothing while the seconds follow one another`() {
        assertThat(servedFrameWarning(previousSecond = 10_000, second = 11_000)).isNull()
    }

    @Test
    fun `says nothing about the first frame`() {
        assertThat(servedFrameWarning(previousSecond = 0, second = 11_000)).isNull()
    }

    @Test
    fun `speaks up when a frame goes back`() {
        // Seen on a Galaxy Watch 4 as the second and minute hands stepping back. Guarded against in
        // two places now, so this line should never appear - which is why it must appear if it does.
        assertThat(servedFrameWarning(previousSecond = 11_000, second = 10_000)).isNotNull()
    }

    @Test
    fun `speaks up when frames stop arriving for a while`() {
        // The system stops asking under load: during an app install the hand was seen moving at 5, 2,
        // 3, 2, 20 and 20 seconds
        assertThat(servedFrameWarning(previousSecond = 10_000, second = 30_000))
            .isEqualTo("a gap of 20 s before 30000")
    }
}
