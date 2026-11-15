package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ThresholdSetupCommandTest {

    @Test
    fun `encode packs the full threshold bundle`() {
        val bytes = ThresholdSetupCommand(
            insulinRemainsThreshold = 100, expiryThreshold = 116,
            maxBasalSpeed = 2.5, maxBolusDose = 15.0, buzzUse = true
        ).encode().toList()
        assertThat(bytes).containsExactly(
            0x1B.toByte(), 100.toByte(), 116.toByte(), 2.toByte(), 50.toByte(), 15.toByte(), 0.toByte(), 0x01.toByte()
        ).inOrder()
    }

    @Test
    fun `buzzUse=false encodes 0x00 (double inversion)`() {
        val bytes = ThresholdSetupCommand(100, 116, 2.5, 15.0, buzzUse = false).encode().toList()
        assertThat(bytes.last()).isEqualTo(0x00.toByte())
    }

    @Test
    fun `insulinRemainsThreshold over 255 is rejected`() {
        // The threshold is a single wire byte, so 300 would wrap to 0x2C = 44 — a silently
        // misprogrammed low-insulin alarm. Reject it loudly instead.
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(300, 116, 2.5, 15.0, buzzUse = true) }
        // 255 is the highest value that still fits the single wire byte.
        val bytes = ThresholdSetupCommand(255, 116, 2.5, 15.0, buzzUse = true).encode()
        assertThat(bytes[1]).isEqualTo(255.toByte())
    }

    @Test
    fun `decode returns result code`() {
        assertThat(ThresholdSetupCommand(100, 116, 2.5, 15.0, true).decode(byteArrayOf(0x7B, 0x00)).resultCode).isEqualTo(0)
    }

    @Test
    fun `out-of-range args throw`() {
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(5, 116, 2.5, 15.0, true) } // insRemain < 10
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(100, 200, 2.5, 15.0, true) } // expiry > 167
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(100, 116, 20.0, 15.0, true) } // basal speed > 15
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(100, 116, 2.5, 30.0, true) } // bolus dose > 25
    }

    @Test
    fun `decode wrong opcode throws`() {
        assertFailsWith<IllegalArgumentException> { ThresholdSetupCommand(100, 116, 2.5, 15.0, true).decode(byteArrayOf(0x7C, 0x00)) }
    }
}
