package app.aaps.plugins.eversense.packets.e365

import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import app.aaps.plugins.eversense.packets.e365.utils.toUnixOrNone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The 365's dates: its zero time means "none". A test transmitter with no sensor sent it as the insertion date. */
@OptIn(ExperimentalUnsignedTypes::class)
class SensorDateTest {

    @Test fun `the zero time is no date`() {
        assertEquals(0L, UByteArray(8).toUnixOrNone())
    }

    @Test fun `a real date is kept`() {
        val time = 1_795_273_025_000L
        assertEquals(time, time.toUnixArray().toUByteArray().toUnixOrNone())
    }
}
