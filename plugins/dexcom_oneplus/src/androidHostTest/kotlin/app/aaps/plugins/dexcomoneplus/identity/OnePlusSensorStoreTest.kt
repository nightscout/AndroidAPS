package app.aaps.plugins.dexcomoneplus.identity

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure part of the session-start anchoring (the store itself needs a Context).
 * Decides whether an explicit sensor start restarts the sensor age.
 */
class OnePlusSensorStoreTest {

    private val sensorA = "AA:BB:CC:DD:EE:01"
    private val sensorB = "AA:BB:CC:DD:EE:02"

    @Test
    fun `nothing stored yet starts a new session`() {
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 0L,
                storedOwnerMac = null,
                previousMac = null,
                deviceAddress = sensorA,
            ),
        ).isTrue()
    }

    @Test
    fun `re-connecting the same sensor keeps its age`() {
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = sensorA,
                previousMac = sensorA,
                deviceAddress = sensorA,
            ),
        ).isFalse()
        // MAC case must not matter (stored uppercase, scan results may differ).
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = sensorA.lowercase(),
                previousMac = null,
                deviceAddress = sensorA,
            ),
        ).isFalse()
    }

    @Test
    fun `another sensor restarts the age`() {
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = sensorA,
                previousMac = sensorA,
                deviceAddress = sensorB,
            ),
        ).isTrue()
    }

    @Test
    fun `a session anchored before the owner key existed falls back to the last stored MAC`() {
        // Sensor started by an older build: session start present, no owner MAC. Without the
        // fallback the same sensor would look new — resetting its age and logging a sensor change.
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = null,
                previousMac = sensorA,
                deviceAddress = sensorA,
            ),
        ).isFalse()
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = null,
                previousMac = sensorA,
                deviceAddress = sensorB,
            ),
        ).isTrue()
    }

    @Test
    fun `an unknown owner with no previous MAC starts a new session`() {
        assertThat(
            OnePlusSensorStore.startsNewSession(
                storedStartMs = 1_000L,
                storedOwnerMac = null,
                previousMac = "",
                deviceAddress = sensorA,
            ),
        ).isTrue()
    }

    @Test
    fun `a serial in a log line shows only its last three characters`() {
        assertThat(OnePlusSensorStore.maskSerial("3MH0A1B2C3")).isEqualTo("***2C3")
        assertThat(OnePlusSensorStore.maskSerial("AB")).isEqualTo("***")
        assertThat(OnePlusSensorStore.maskSerial("")).isEqualTo("-")
        assertThat(OnePlusSensorStore.maskSerial(null)).isEqualTo("-")
    }
}
