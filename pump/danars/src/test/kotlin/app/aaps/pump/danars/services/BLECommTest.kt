package app.aaps.pump.danars.services

import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
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
import org.mockito.Mock
import org.mockito.Mockito.`when`

class BLECommTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var danaRSMessageHashTable: DanaRSMessageHashTable
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var danaRSPlugin: DanaRSPlugin
    @Mock lateinit var bleEncryption: BleEncryption
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var danaRSPacket: DanaRSPacket
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var bleTransport: BleTransport
    @Mock lateinit var bleAdapter: BleAdapter
    @Mock lateinit var bleScanner: BleScanner
    @Mock lateinit var bleGatt: BleGatt

    private lateinit var bleComm: BLEComm

    @BeforeEach
    fun setup() {
        `when`(bleTransport.adapter).thenReturn(bleAdapter)
        `when`(bleTransport.scanner).thenReturn(bleScanner)
        `when`(bleTransport.gatt).thenReturn(bleGatt)

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
            preferences,
            configBuilder,
            notificationManager,
            bleTransport
        )

        `when`(rh.gs(anyInt())).thenReturn("test")
    }

    @Test
    fun testInitialState() {
        assertThat(bleComm.isConnected).isFalse()
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
        `when`(bleAdapter.getDeviceName("00:11:22:33:44:55")).thenReturn(null)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        assertThat(result).isFalse()
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun testConnect_deviceNotBonded() {
        `when`(bleAdapter.getDeviceName("00:11:22:33:44:55")).thenReturn("DanaRS")
        `when`(bleAdapter.isDeviceBonded("00:11:22:33:44:55")).thenReturn(false)

        val result = bleComm.connect("test", "00:11:22:33:44:55")

        assertThat(result).isFalse()
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
    fun testSendMessage_notConnected() {
        `when`(danaRSPacket.friendlyName).thenReturn("TestPacket")

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
