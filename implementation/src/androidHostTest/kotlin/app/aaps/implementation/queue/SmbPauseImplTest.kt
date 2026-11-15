package app.aaps.implementation.queue

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.SmbPause
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** #5381: a pause holds back SMBs while the user confirms a bolus, and it always ends, also when nobody ends it. */
class SmbPauseImplTest {

    private var now = 1_000_000L
    private val dateUtil: DateUtil = mock { on { now() } doAnswer { now } }
    private val pause = SmbPauseImpl(mock<AAPSLogger>(), dateUtil)

    private fun passes(time: Duration) {
        now += time.inWholeMilliseconds
    }

    @Test
    fun `a pause holds back SMBs until it ends`() {
        val id = pause.begin()
        assertThat(pause.isActive()).isTrue()
        assertThat(pause.isActive(id)).isTrue()

        pause.end(id)

        assertThat(pause.isActive()).isFalse()
        assertThat(pause.isActive(id)).isFalse()
    }

    @Test
    fun `a pause nobody ends runs out by itself`() {
        val id = pause.begin()

        passes(SmbPause.MAX_DURATION - 1.milliseconds)
        assertThat(pause.isActive(id)).isTrue()

        passes(1.milliseconds)
        assertThat(pause.isActive(id)).isFalse()
        assertThat(pause.isActive()).isFalse()
    }

    @Test
    fun `SMBs wait while any pause is on`() {
        val first = pause.begin()
        val second = pause.begin()
        assertThat(second).isNotEqualTo(first)

        pause.end(first)
        assertThat(pause.isActive()).isTrue()

        pause.end(second)
        assertThat(pause.isActive()).isFalse()
    }

    @Test
    fun `after the hand over the pause lasts only a short time`() {
        val id = pause.begin()

        pause.handOver(id)

        passes(SmbPause.HAND_OVER - 1.milliseconds)
        assertThat(pause.isActive(id)).isTrue()
        passes(1.milliseconds)
        assertThat(pause.isActive(id)).isFalse()
    }

    @Test
    fun `a pause confirmed just before its end still covers the hand over`() {
        val id = pause.begin()
        passes(SmbPause.MAX_DURATION - 1.seconds)

        pause.handOver(id)

        passes(SmbPause.HAND_OVER - 1.seconds)
        assertThat(pause.isActive(id)).isTrue()
    }

    @Test
    fun `a pause that ran out cannot be handed over`() {
        val id = pause.begin()
        passes(SmbPause.MAX_DURATION)

        pause.handOver(id)

        assertThat(pause.isActive(id)).isFalse()
        assertThat(pause.isActive()).isFalse()
    }
}
