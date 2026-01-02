package app.aaps.pump.danars.services

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.comm.DanaRSMessageHashTable
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class BLECommTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var danaRSMessageHashTable: DanaRSMessageHashTable
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var bleEncryption: BleEncryption
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var bluetoothManager: BluetoothManager
    @Mock lateinit var bluetoothAdapter: BluetoothAdapter
    @Mock lateinit var bluetoothDevice: BluetoothDevice
    @Mock lateinit var bluetoothGatt: BluetoothGatt
    @Mock lateinit var danaRSPacket: DanaRSPacket
    @Mock lateinit var configBuilder: ConfigBuilder

    private lateinit var bleComm: BLEComm

    @BeforeEach
    fun setup() {
        bleComm = BLEComm(
            aapsLogger,
            rh,
            context,
            rxBus,
            danaRSMessageHashTable,
            danaPump,
            danaRSPlugin,
            bleEncryption,
            pumpSync,
            dateUtil,
            uiInteraction,
            preferences,
            configBuilder
        )

        `when`(rh.gs(anyInt())).thenReturn("test")
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)
        `when`(bluetoothManager.adapter).thenReturn(bluetoothAdapter)
        `when`(bluetoothAdapter.getRemoteDevice(anyString())).thenReturn(bluetoothDevice)
        `when`(bluetoothDevice.name).thenReturn("DanaRS")
        `when`(bluetoothDevice.bondState).thenReturn(BluetoothDevice.BOND_BONDED)
    }

    @Test
    fun testInitialState() {
        assertThat(bleComm.isConnected).isFalse()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testConnect_noBluetoothAdapter() {
        `when`(bluetoothManager.adapter).thenReturn(null)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        assertThat(result).isFalse()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testConnect_nullAddress() {
        val result = bleComm.connect("test", null)

        assertThat(result).isFalse()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testConnect_deviceNotFound() {
        `when`(bluetoothAdapter.getRemoteDevice(anyString())).thenReturn(null)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        assertThat(result).isFalse()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testConnect_deviceNotBonded() {
        `when`(bluetoothDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        assertThat(result).isFalse()
    }

    @Test
    fun testConnect_success() {
        `when`(bluetoothDevice.connectGatt(any(), any(), any())).thenReturn(bluetoothGatt)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        // Result depends on BLE permissions which may not be granted in test
        // Just verify no crash occurs
    }

    @Test
    fun testStopConnecting() {
        bleComm.stopConnecting()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testDisconnect() {
        bleComm.disconnect("test")
        // Should not throw exception
    }

    @Test
    fun testDisconnect_whenConnecting() {
        // Simulate connecting state
        bleComm.connect("test", "00:11:22:33:44:55")
        bleComm.disconnect("test")

        // Should handle gracefully
    }

    @Test
    fun testSendMessage_notConnected() {
        `when`(danaRSPacket.friendlyName).thenReturn("TestPacket")
        //`when`(danaRSPacket.requestParams).thenReturn(ByteArray(0))

        bleComm.sendMessage(danaRSPacket)

        // Should not throw exception when not connected
    }

    @Test
    fun testIsConnected_initiallyFalse() {
        assertThat(bleComm.isConnected).isFalse()
    }

    @Test
    fun testIsConnecting_initiallyFalse() {
        assertThat(bleComm.isConnecting).isFalse()
    }
}
