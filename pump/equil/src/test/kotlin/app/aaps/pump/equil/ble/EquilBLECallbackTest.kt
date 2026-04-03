package app.aaps.pump.equil.ble

import android.bluetooth.BluetoothGatt
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.pump.equil.driver.definition.BluetoothConnectionState
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for EquilBLE's BleTransportListener callback behavior.
 *
 * These tests verify that the refactored EquilBLE (which delegates BLE operations
 * to EquilBleTransportImpl) behaves identically to the original monolithic EquilBLE.
 */
class EquilBLECallbackTest : TestBase() {

    @Mock private lateinit var bleTransport: EquilBleTransport
    @Mock private lateinit var gatt: BleGatt
    @Mock private lateinit var scanner: BleScanner
    @Mock private lateinit var bleAdapter: app.aaps.core.interfaces.pump.ble.BleAdapter
    @Mock private lateinit var equilManager: EquilManager

    private lateinit var equilBLE: EquilBLE

    @BeforeEach
    fun setUp() {
        whenever(bleTransport.gatt).thenReturn(gatt)
        whenever(bleTransport.scanner).thenReturn(scanner)
        whenever(bleTransport.adapter).thenReturn(bleAdapter)
        equilBLE = EquilBLE(aapsLogger, bleTransport, rxBus)
    }

    private val equilState = EquilManager.EquilState().apply { address = "AA:BB:CC:DD:EE:FF" }

    private fun initWithManager() {
        whenever(equilManager.equilState).thenReturn(equilState)
        equilBLE.init(equilManager)
    }

    // --- onConnectionStateChanged(true) ---

    @Test
    fun `onConnectionStateChanged true should set isConnected`() {
        initWithManager()
        equilBLE.connecting = true

        equilBLE.onConnectionStateChanged(true)

        assertTrue(equilBLE.isConnected)
        assertFalse(equilBLE.connecting)
    }

    @Test
    fun `onConnectionStateChanged true should call discoverServices`() {
        initWithManager()

        equilBLE.onConnectionStateChanged(true)

        verify(gatt).discoverServices()
    }

    @Test
    fun `onConnectionStateChanged true should set bluetoothConnectionState to CONNECTED`() {
        initWithManager()

        equilBLE.onConnectionStateChanged(true)

        assertEquals(BluetoothConnectionState.CONNECTED, equilState.bluetoothConnectionState)
    }

    // --- onConnectionStateChanged(false) ---

    @Test
    fun `onConnectionStateChanged false should call disconnect`() {
        initWithManager()
        equilBLE.isConnected = true

        equilBLE.onConnectionStateChanged(false)

        assertFalse(equilBLE.isConnected)
        verify(gatt).disconnect()
        verify(gatt).close()
    }

    @Test
    fun `onConnectionStateChanged false should clear connecting`() {
        initWithManager()
        equilBLE.connecting = true

        equilBLE.onConnectionStateChanged(false)

        assertFalse(equilBLE.connecting)
    }

    @Test
    fun `onConnectionStateChanged false without baseCmd should not crash`() {
        initWithManager()

        // No command is active — disconnect should still work cleanly
        equilBLE.onConnectionStateChanged(false)

        assertFalse(equilBLE.isConnected)
        verify(gatt).disconnect()
        verify(gatt).close()
    }

    // --- onGattError133 callback ---

    @Test
    fun `init should register onGattError133 callback`() {
        initWithManager()

        // The callback should have been registered
        verify(bleTransport).onGattError133 = any()
    }

    @Test
    fun `init should register listener on transport`() {
        initWithManager()

        verify(bleTransport).setListener(equilBLE)
    }

    // --- onServicesDiscovered ---

    @Test
    fun `onServicesDiscovered true should call enableNotifications`() {
        initWithManager()

        equilBLE.onServicesDiscovered(true)

        verify(gatt).enableNotifications()
    }

    @Test
    fun `onServicesDiscovered true should call requestConnectionPriority HIGH`() {
        initWithManager()

        equilBLE.onServicesDiscovered(true)

        verify(gatt).requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    @Test
    fun `onServicesDiscovered false should not call enableNotifications`() {
        initWithManager()

        equilBLE.onServicesDiscovered(false)

        verify(gatt, never()).enableNotifications()
        verify(gatt, never()).requestConnectionPriority(any())
    }

    // --- onDescriptorWritten ---

    @Test
    fun `onDescriptorWritten should call ready when baseCmd is null`() {
        initWithManager()

        // With no command set, ready() should just return without error
        equilBLE.onDescriptorWritten()

        // No crash = success. ready() logs and returns if baseCmd is null.
    }

    // --- onCharacteristicChanged ---

    @Test
    fun `onCharacteristicChanged should call requestConnectionPriority`() {
        initWithManager()

        equilBLE.onCharacteristicChanged(byteArrayOf(0x01, 0x02))

        verify(gatt).requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
    }

    // --- disconnect ---

    @Test
    fun `disconnect should call gatt disconnect and close`() {
        initWithManager()
        equilBLE.isConnected = true

        equilBLE.disconnect()

        verify(gatt).disconnect()
        verify(gatt).close()
        assertFalse(equilBLE.isConnected)
    }

    @Test
    fun `disconnect should set bluetoothConnectionState to DISCONNECTED`() {
        initWithManager()

        equilBLE.disconnect()

        assertEquals(BluetoothConnectionState.DISCONNECTED, equilState.bluetoothConnectionState)
    }

    @Test
    fun `disconnect should reset autoScan`() {
        initWithManager()
        equilBLE.autoScan = true

        equilBLE.disconnect()

        assertFalse(equilBLE.autoScan)
    }

    // --- unBond ---

    @Test
    fun `unBond should delegate to bleTransport adapter`() {
        initWithManager()

        equilBLE.unBond("AA:BB:CC:DD:EE:FF")

        verify(bleAdapter).removeBond("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `unBond with null should not call removeBond`() {
        initWithManager()

        equilBLE.unBond(null)

        verify(bleAdapter, never()).removeBond(any())
    }

    // --- init ---

    @Test
    fun `init should set macAddress from equilManager`() {
        initWithManager()

        assertEquals("AA:BB:CC:DD:EE:FF", equilBLE.macAddress)
    }

    // --- stopScan ---

    @Test
    fun `stopScan should delegate to scanner`() {
        initWithManager()

        equilBLE.stopScan()

        verify(scanner).stopScan()
    }
}
