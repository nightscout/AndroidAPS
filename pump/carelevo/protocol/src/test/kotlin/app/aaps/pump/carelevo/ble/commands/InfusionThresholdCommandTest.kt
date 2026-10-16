package app.aaps.pump.carelevo.ble.commands

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class InfusionThresholdCommandTest {

    @Test
    fun `encode max-speed flag 0x00 with unit-centi value`() {
        assertThat(InfusionThresholdCommand(isMaxVolume = false, value = 2.5).encode().toList())
            .containsExactly(0x17.toByte(), 0x00.toByte(), 2.toByte(), 50.toByte()).inOrder()
    }

    @Test
    fun `encode max-volume flag 0x01`() {
        assertThat(InfusionThresholdCommand(isMaxVolume = true, value = 15.0).encode().toList())
            .containsExactly(0x17.toByte(), 0x01.toByte(), 15.toByte(), 0.toByte()).inOrder()
    }

    @Test
    fun `encode rounds fractional to centi`() {
        assertThat(InfusionThresholdCommand(isMaxVolume = false, value = 0.05).encode().toList())
            .containsExactly(0x17.toByte(), 0x00.toByte(), 0.toByte(), 5.toByte()).inOrder()
    }

    @Test
    fun `decode returns type and result`() {
        val r = InfusionThresholdCommand(isMaxVolume = true, value = 15.0).decode(byteArrayOf(0x77, 0x01, 0x00))
        assertThat(r.type).isEqualTo(1)
        assertThat(r.resultCode).isEqualTo(0)
    }

    @Test
    fun `max-speed above range throws`() {
        assertFailsWith<IllegalArgumentException> { InfusionThresholdCommand(isMaxVolume = false, value = 20.0) }
    }

    @Test
    fun `max-volume above range throws`() {
        assertFailsWith<IllegalArgumentException> { InfusionThresholdCommand(isMaxVolume = true, value = 30.0) }
    }

    @Test
    fun `below minimum throws`() {
        assertFailsWith<IllegalArgumentException> { InfusionThresholdCommand(isMaxVolume = false, value = 0.0) }
    }

    @Test
    fun `decode too short throws`() {
        assertFailsWith<IllegalArgumentException> {
            InfusionThresholdCommand(isMaxVolume = true, value = 15.0).decode(byteArrayOf(0x77, 0x01))
        }
    }
}
