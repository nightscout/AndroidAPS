package app.aaps.plugins.source

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * When a Libre 3 sensor start is written down.
 *
 * The event decides where one sensor stops and the next one starts, so writing one too many is as
 * wrong as writing none: a second event would cut the running session in two and throw away every
 * calibration entry made before it.
 */
class Libre3SensorChangeTest {

    private val nowMs = 1_777_216_508_000L
    private val activatedAtMs = nowMs - 3 * 60 * 60 * 1000L

    @Test
    fun `the first start of a sensor is written`() {
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = "MH0123456", activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isEqualTo("MH0123456")
    }

    @Test
    fun `the same sensor is never written twice`() {
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = "MH0123456", serialNumber = "MH0123456", activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isNull()
    }

    @Test
    fun `a serial written in another case is still the same sensor`() {
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = "mh0123456", serialNumber = "MH0123456", activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isNull()
    }

    @Test
    fun `another sensor is written`() {
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = "MH0123456", serialNumber = "MH9999999", activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isEqualTo("MH9999999")
    }

    @Test
    fun `nothing is written for a sensor with no name`() {
        // An event that cannot be tied to a serial could not be kept unique, and every reading
        // would write one more.
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = null, activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isNull()
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = "  ", activatedAtMs = activatedAtMs, nowMs = nowMs)
        ).isNull()
    }

    @Test
    fun `an unknown start is not written`() {
        // Zero means the scan learned nothing. The event would land in 1970 and the sensor would
        // read as endlessly old.
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = "MH0123456", activatedAtMs = 0L, nowMs = nowMs)
        ).isNull()
    }

    @Test
    fun `a start in the future is not written`() {
        // Only a wrong phone clock or a bad read can produce this, and neither belongs in the
        // database, where it would push the calibration warm-up window ahead of the present.
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = "MH0123456", activatedAtMs = nowMs + 60_000L, nowMs = nowMs)
        ).isNull()
    }

    @Test
    fun `a start exactly now is written`() {
        // A sensor this phone activates is started at this very moment.
        assertThat(
            Libre3SensorChange.serialToLog(loggedSerial = null, serialNumber = "MH0123456", activatedAtMs = nowMs, nowMs = nowMs)
        ).isEqualTo("MH0123456")
    }
}
