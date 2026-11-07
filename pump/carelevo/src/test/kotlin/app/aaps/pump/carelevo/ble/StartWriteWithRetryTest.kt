package app.aaps.pump.carelevo.ble

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** A write Android refuses while another GATT operation is pending is tried again, not dropped. */
internal class StartWriteWithRetryTest {

    private var slept = 0L
    private val sleep: (Long) -> Unit = { slept += it }

    @Test
    fun `a write that starts at once does not wait`() {
        assertThat(startWriteWithRetry(sleep) { true }).isEqualTo(0L)
        assertThat(slept).isEqualTo(0L)
    }

    /** The case seen on a real patch: the write of the read before was still pending for a moment. */
    @Test
    fun `a write refused while the GATT is busy starts once it is free`() {
        var tries = 0

        val waited = startWriteWithRetry(sleep) { ++tries > 3 }

        assertThat(tries).isEqualTo(4)
        assertThat(waited).isEqualTo(3 * WRITE_BUSY_STEP_MS)
    }

    @Test
    fun `a write that never starts gives up after the wait`() {
        val waited = startWriteWithRetry(sleep) { false }

        assertThat(waited).isNull()
        assertThat(slept).isEqualTo(WRITE_BUSY_WAIT_MS)
    }
}
