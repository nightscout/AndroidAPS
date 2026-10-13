package app.aaps.pump.equil.ble

import android.content.Context
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mock

class EquilBLETest : TestBase() {

    @Mock private lateinit var context: Context

    @Test
    fun `isConnected should be false initially`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        assertFalse(equilBLE.isConnected)
    }

    @Test
    fun `connecting should be false initially`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        assertFalse(equilBLE.connecting)
    }

    @Test
    fun `macAddress should be null initially`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        assertNull(equilBLE.macAddress)
    }

    @Test
    fun `TIME_OUT_WHAT constant should have correct value`() {
        assertEquals(0x12, EquilBLE.TIME_OUT_WHAT)
    }

    @Test
    fun `TIME_OUT_CONNECT_WHAT constant should have correct value`() {
        assertEquals(0x13, EquilBLE.TIME_OUT_CONNECT_WHAT)
    }

    @Test
    fun `TIME_OUT_WHAT should equal 18 in decimal`() {
        assertEquals(18, EquilBLE.TIME_OUT_WHAT)
    }

    @Test
    fun `TIME_OUT_CONNECT_WHAT should equal 19 in decimal`() {
        assertEquals(19, EquilBLE.TIME_OUT_CONNECT_WHAT)
    }

    @Test
    fun `constants should be different values`() {
        assert(EquilBLE.TIME_OUT_WHAT != EquilBLE.TIME_OUT_CONNECT_WHAT)
    }

    @Test
    fun `macAddress can be set`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        val testMac = "00:11:22:33:44:55"
        equilBLE.macAddress = testMac
        assertEquals(testMac, equilBLE.macAddress)
    }

    @Test
    fun `isConnected can be set`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        equilBLE.isConnected = true
        assert(equilBLE.isConnected)
    }

    @Test
    fun `connecting can be set`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        equilBLE.connecting = true
        assert(equilBLE.connecting)
    }

    @Test
    fun `multiple EquilBLE instances should have independent state`() {
        val ble1 = EquilBLE(aapsLogger, context, rxBus)
        val ble2 = EquilBLE(aapsLogger, context, rxBus)

        ble1.isConnected = true
        ble2.isConnected = false

        assert(ble1.isConnected)
        assertFalse(ble2.isConnected)
    }

    @Test
    fun `macAddress can be changed`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        equilBLE.macAddress = "00:11:22:33:44:55"
        assertEquals("00:11:22:33:44:55", equilBLE.macAddress)

        equilBLE.macAddress = "AA:BB:CC:DD:EE:FF"
        assertEquals("AA:BB:CC:DD:EE:FF", equilBLE.macAddress)
    }

    @Test
    fun `isConnected state can toggle`() {
        val equilBLE = EquilBLE(aapsLogger, context, rxBus)
        assertFalse(equilBLE.isConnected)

        equilBLE.isConnected = true
        assert(equilBLE.isConnected)

        equilBLE.isConnected = false
        assertFalse(equilBLE.isConnected)
    }
}
