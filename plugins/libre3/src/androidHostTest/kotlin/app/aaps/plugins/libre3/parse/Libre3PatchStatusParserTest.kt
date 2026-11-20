package app.aaps.plugins.libre3.parse

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The health message of the sensor, 12 plain bytes. */
class Libre3PatchStatusParserTest {

    private fun status(
        lifeCount: Int = 1000,
        errorData: Int = 0,
        patchState: Int = 4,
    ): ByteArray {
        val out = ByteArray(Libre3PatchStatusParser.PLAINTEXT_SIZE)
        putS16(out, 0, lifeCount)
        putS16(out, 2, errorData)
        putS16(out, 4, -500)
        out[6] = 2
        out[7] = patchState.toByte()
        putS16(out, 8, lifeCount)
        return out
    }

    private fun putS16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    @Test
    fun `a healthy sensor is read as running with no fault`() {
        val parsed = Libre3PatchStatusParser.parse(status())

        assertThat(parsed.lifeCount).isEqualTo(1000)
        assertThat(parsed.patchState.isActive).isTrue()
        assertThat(parsed.sensorError).isEqualTo(Libre3SensorError.NONE)
        assertThat(parsed.hasSensorError()).isFalse()
    }

    @Test
    fun `the event number is shifted back up by four thousand`() {
        val parsed = Libre3PatchStatusParser.parse(status())

        assertThat(parsed.eventData).isEqualTo(3500)
    }

    @Test
    fun `every known fault code is read`() {
        assertThat(Libre3SensorError.fromCode(0)).isEqualTo(Libre3SensorError.NONE)
        assertThat(Libre3SensorError.fromCode(3)).isEqualTo(Libre3SensorError.INSERTION_FAILURE)
        assertThat(Libre3SensorError.fromCode(5)).isEqualTo(Libre3SensorError.EXPIRED)
        assertThat(Libre3SensorError.fromCode(6)).isEqualTo(Libre3SensorError.TERMINATED)
        assertThat(Libre3SensorError.fromCode(8)).isEqualTo(Libre3SensorError.TERMINATED)
        assertThat(Libre3SensorError.fromCode(7)).isEqualTo(Libre3SensorError.TRANSMISSION_ERROR)
    }

    @Test
    fun `a fault code this driver does not know is treated as a fault`() {
        val parsed = Libre3PatchStatusParser.parse(status(errorData = 99))

        assertThat(parsed.sensorError).isEqualTo(Libre3SensorError.UNKNOWN)
        assertThat(parsed.hasSensorError()).isTrue()
    }

    @Test
    fun `a sensor that failed at insertion, ended or was shut down is a fault`() {
        for (code in listOf(3, 5, 6, 8)) {
            assertThat(Libre3PatchStatusParser.parse(status(errorData = code)).hasSensorError()).isTrue()
        }
    }

    @Test
    fun `a sending problem alone does not bother the user`() {
        val parsed = Libre3PatchStatusParser.parse(status(errorData = 7))

        assertThat(parsed.sensorError).isEqualTo(Libre3SensorError.TRANSMISSION_ERROR)
        assertThat(parsed.hasSensorError()).isFalse()
    }

    @Test
    fun `a bad state is a fault even when no code came with it`() {
        for (state in listOf(3, 5, 6, 7, 8)) {
            val parsed = Libre3PatchStatusParser.parse(status(patchState = state))

            assertThat(parsed.patchState.isActive).isFalse()
            assertThat(parsed.hasSensorError()).isTrue()
        }
    }

    @Test
    fun `ended and shut down are told apart`() {
        assertThat(Libre3PatchState(5).isExpiredOrError).isTrue()
        assertThat(Libre3PatchState(5).isTerminated).isFalse()
        assertThat(Libre3PatchState(6).isTerminated).isTrue()
        assertThat(Libre3PatchState(8).isTerminated).isTrue()
    }

    @Test
    fun `a negative number keeps its sign`() {
        val raw = status()
        putS16(raw, 2, -3)

        assertThat(Libre3PatchStatusParser.parse(raw).errorData).isEqualTo(-3)
    }

    @Test
    fun `a message of the wrong size is refused`() {
        assertThrows<Libre3ParseException> { Libre3PatchStatusParser.parse(ByteArray(11)) }
    }
}
