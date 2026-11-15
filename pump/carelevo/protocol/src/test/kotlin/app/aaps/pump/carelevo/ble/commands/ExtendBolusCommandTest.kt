package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ExtendBolusCommandTest {

    @Test
    fun `encode immediate and extended doses as unit-centi plus hour and min`() {
        assertThat(ExtendBolusCommand(immediateDose = 1.5, extendedSpeed = 0.75, hour = 2, min = 30).encode().toList())
            .containsExactly(
                0x25.toByte(), 1.toByte(), 50.toByte(), 0.toByte(), 75.toByte(), 2.toByte(), 30.toByte()
            ).inOrder()
    }

    @Test
    fun `encode rounds fractional doses to centi`() {
        assertThat(ExtendBolusCommand(immediateDose = 0.05, extendedSpeed = 12.34, hour = 0, min = 0).encode().toList())
            .containsExactly(
                0x25.toByte(), 0.toByte(), 5.toByte(), 12.toByte(), 34.toByte(), 0.toByte(), 0.toByte()
            ).inOrder()
    }

    @Test
    fun `decode returns result and expected time in seconds`() {
        val r = ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 1.0, hour = 1, min = 0)
            .decode(byteArrayOf(0x85.toByte(), 0x00, 0x03, 0x14))
        assertThat(r.resultCode).isEqualTo(0)
        assertThat(r.expectedTimeSeconds).isEqualTo(200) // 3*60 + 20
    }

    @Test
    fun `hour above range throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 1.0, hour = 25, min = 0)
        }
    }

    @Test
    fun `min above range throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 1.0, hour = 0, min = 60)
        }
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 1.0, hour = 1, min = 0)
                .decode(byteArrayOf(0x86.toByte(), 0x00, 0x00, 0x00))
        }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            ExtendBolusCommand(immediateDose = 1.0, extendedSpeed = 1.0, hour = 1, min = 0)
                .decode(byteArrayOf(0x85.toByte(), 0x00, 0x00))
        }
    }
}
