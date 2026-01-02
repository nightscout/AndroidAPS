package app.aaps.pump.equil.ble

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class GattAttributesTest : TestBase() {

    @Test
    fun `SERVICE_RADIO should have correct UUID`() {
        assertEquals("0000f000-0000-1000-8000-00805f9b34fb", GattAttributes.SERVICE_RADIO)
    }

    @Test
    fun `NRF_UART_NOTIFY should have correct UUID`() {
        assertEquals("0000f001-0000-1000-8000-00805f9b34fb", GattAttributes.NRF_UART_NOTIFY)
    }

    @Test
    fun `NRF_UART_WRITE should have correct UUID`() {
        assertEquals("0000f001-0000-1000-8000-00805f9b34fb", GattAttributes.NRF_UART_WRITE)
    }

    @Test
    fun `NRF_UART_NOTIFY and NRF_UART_WRITE should be the same`() {
        assertEquals(GattAttributes.NRF_UART_NOTIFY, GattAttributes.NRF_UART_WRITE)
    }

    @Test
    fun `characteristicConfigDescriptor should be valid UUID`() {
        assertNotNull(GattAttributes.characteristicConfigDescriptor)
    }

    @Test
    fun `characteristicConfigDescriptor should have correct UUID`() {
        val expected = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        assertEquals(expected, GattAttributes.characteristicConfigDescriptor)
    }

    @Test
    fun `SERVICE_RADIO should be valid UUID format`() {
        // Should not throw exception
        UUID.fromString(GattAttributes.SERVICE_RADIO)
    }

    @Test
    fun `NRF_UART_NOTIFY should be valid UUID format`() {
        // Should not throw exception
        UUID.fromString(GattAttributes.NRF_UART_NOTIFY)
    }

    @Test
    fun `NRF_UART_WRITE should be valid UUID format`() {
        // Should not throw exception
        UUID.fromString(GattAttributes.NRF_UART_WRITE)
    }

    @Test
    fun `all UUIDs should be lowercase`() {
        assertEquals(GattAttributes.SERVICE_RADIO.lowercase(), GattAttributes.SERVICE_RADIO)
        assertEquals(GattAttributes.NRF_UART_NOTIFY.lowercase(), GattAttributes.NRF_UART_NOTIFY)
        assertEquals(GattAttributes.NRF_UART_WRITE.lowercase(), GattAttributes.NRF_UART_WRITE)
    }
}
