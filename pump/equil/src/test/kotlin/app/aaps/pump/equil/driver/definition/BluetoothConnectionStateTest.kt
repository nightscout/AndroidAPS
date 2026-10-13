package app.aaps.pump.equil.driver.definition

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BluetoothConnectionStateTest : TestBase() {

    @Test
    fun `all enum values should be accessible`() {
        val allStates = BluetoothConnectionState.entries
        assertEquals(3, allStates.size)
        assert(allStates.contains(BluetoothConnectionState.CONNECTING))
        assert(allStates.contains(BluetoothConnectionState.CONNECTED))
        assert(allStates.contains(BluetoothConnectionState.DISCONNECTED))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(BluetoothConnectionState.CONNECTING, BluetoothConnectionState.valueOf("CONNECTING"))
        assertEquals(BluetoothConnectionState.CONNECTED, BluetoothConnectionState.valueOf("CONNECTED"))
        assertEquals(BluetoothConnectionState.DISCONNECTED, BluetoothConnectionState.valueOf("DISCONNECTED"))
    }

    @Test
    fun `enum toString should return name`() {
        assertEquals("CONNECTING", BluetoothConnectionState.CONNECTING.toString())
        assertEquals("CONNECTED", BluetoothConnectionState.CONNECTED.toString())
        assertEquals("DISCONNECTED", BluetoothConnectionState.DISCONNECTED.toString())
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, BluetoothConnectionState.CONNECTING.ordinal)
        assertEquals(1, BluetoothConnectionState.CONNECTED.ordinal)
        assertEquals(2, BluetoothConnectionState.DISCONNECTED.ordinal)
    }

    @Test
    fun `all states should be unique`() {
        val states = BluetoothConnectionState.entries
        val uniqueStates = states.toSet()
        assertEquals(states.size, uniqueStates.size)
    }

    @Test
    fun `connection lifecycle states should be ordered logically`() {
        // Typically: DISCONNECTED -> CONNECTING -> CONNECTED
        // But enum order is: CONNECTING, CONNECTED, DISCONNECTED
        val states = BluetoothConnectionState.entries
        assertEquals(BluetoothConnectionState.CONNECTING, states[0])
        assertEquals(BluetoothConnectionState.CONNECTED, states[1])
        assertEquals(BluetoothConnectionState.DISCONNECTED, states[2])
    }
}
