package app.aaps.plugins.libre3.reconnect

import app.aaps.core.interfaces.source.SensorSlot
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The pre-soak slot must be slower than the slot that feeds the loop, and never the other way. */
class Libre3ReconnectPolicySlotTest {

    @Test
    fun `the production pace is exactly the pace it always had`() {
        for (attempt in 1..Libre3ReconnectPolicy.MAX_ATTEMPTS + 2) {
            assertThat(Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.PRODUCTION))
                .isEqualTo(Libre3ReconnectPolicy.nextDelayMs(attempt))
        }
    }

    @Test
    fun `the pre-soak slot never retries faster than its floor`() {
        for (attempt in 1..Libre3ReconnectPolicy.MAX_ATTEMPTS + 2) {
            assertThat(Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.STAGING))
                .isAtLeast(Libre3ReconnectPolicy.STAGING_MIN_RETRY_MS)
        }
    }

    @Test
    fun `the pre-soak slot always waits longer than production, so it cannot crowd it out`() {
        for (attempt in 1..Libre3ReconnectPolicy.MAX_ATTEMPTS + 2) {
            val production = Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.PRODUCTION)
            val staging = Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.STAGING)

            assertThat(staging).isGreaterThan(production)
        }
    }

    @Test
    fun `the two slots do not knock at the same instant once the ladder is spent`() {
        val attempt = Libre3ReconnectPolicy.MAX_ATTEMPTS
        val production = Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.PRODUCTION)

        assertThat(Libre3ReconnectPolicy.nextDelayMs(attempt, SensorSlot.STAGING))
            .isEqualTo(production + Libre3ReconnectPolicy.STAGING_RETRY_OFFSET_MS)
    }
}
