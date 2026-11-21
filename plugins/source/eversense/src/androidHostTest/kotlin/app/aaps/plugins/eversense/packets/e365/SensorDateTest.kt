package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import app.aaps.plugins.eversense.packets.e365.utils.toUnixOrNone
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The 365's dates: its zero time means "none". A test transmitter with no sensor sent it as the insertion date. */
class SensorDateTest {

    @Test fun `the zero time is no date`() {
        assertThat(UByteArray(8).toUnixOrNone()).isEqualTo(0L)
    }

    @Test fun `a real date is kept`() {
        val time = 1_795_273_025_000L
        assertThat(time.toUnixArray().toUByteArray().toUnixOrNone()).isEqualTo(time)
    }
}
