package app.aaps.pump.danars.services

import android.content.Context
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.BleAdapter
import app.aaps.core.interfaces.pump.ble.BleGatt
import app.aaps.core.interfaces.pump.ble.BleScanner
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.resources.ResourceHelper
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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicReference

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

    private companion object { private const val ADDRESS = "00:11:22:33:44:55" }

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

    /**
     * One connection legitimately produces two `onDescriptorWritten` callbacks — [BLEComm.connect] enables
     * notifications eagerly and `findCharacteristic` enables them again after service discovery. Only the first may
     * drive the handshake.
     *
     * The second used to republish [PairingStep.HANDSHAKE_IN_PROGRESS], which downgrades the pair wizard from a
     * user-input step (ENTER_PIN / ENTER_PASSWORD) back to the progress spinner and restarts no timeout — a
     * first-time RSv3 pairing then hung on the spinner forever instead of accepting the PINs.
     */
    @Test
    fun onDescriptorWritten_duplicateForSameConnection_doesNotRepublishHandshake() {
        bleComm.onDescriptorWritten()
        bleComm.onDescriptorWritten()

        verify(bleTransport, times(1)).updatePairingState(PairingState(step = PairingStep.HANDSHAKE_IN_PROGRESS))
    }

    @Test
    fun onDescriptorWritten_afterReconnect_drivesTheHandshakeAgain() {
        `when`(bleAdapter.getDeviceName(ADDRESS)).thenReturn("Dana")
        `when`(bleAdapter.isDeviceBonded(ADDRESS)).thenReturn(true)
        `when`(bleGatt.connect(ADDRESS)).thenReturn(true)

        bleComm.onDescriptorWritten()
        // A new connection must clear the guard, or the pump could never be reconnected after a drop.
        bleComm.connect("test", ADDRESS)
        bleComm.onDescriptorWritten()

        verify(bleTransport, times(2)).updatePairingState(PairingState(step = PairingStep.HANDSHAKE_IN_PROGRESS))
    }

    /** A well framed packet: A5 A5 LEN ... 5A 5A, the shape readDataParsing accepts. */
    private fun framedPacket(): ByteArray {
        val length = 3
        return ByteArray(length + 7).apply {
            this[0] = 0xA5.toByte()
            this[1] = 0xA5.toByte()
            this[2] = length.toByte()
            this[length + 5] = 0x5A.toByte()
            this[length + 6] = 0x5A.toByte()
        }
    }

    /**
     * A packet that fails its integrity check is dropped, not fatal.
     *
     * `BleEncryption.getDecryptedPacket` returns null for exactly two reasons: the length byte
     * disagreeing with the decrypted size, or a bad CRC. Both are ordinary events on a radio link -
     * that is what a checksum is for. `readDataParsing` used to end with
     * `checkNotNull(decrypted) { "Null decryptedInputBuffer" }`, so one corrupt packet threw
     * IllegalStateException out of the BLE callback and killed the app, taking the loop with it. Seen
     * in the field on 4.0.0-dev-c.
     */
    @Test
    fun aPacketThatFailsItsIntegrityCheckIsDroppedNotFatal() {
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(null)

        bleComm.onCharacteristicChanged(framedPacket())

        verify(bleEncryption, times(1)).getDecryptedPacket(any())
    }

    /** Dropping the packet must not wedge the stream - the next one is still parsed. */
    @Test
    fun theNextPacketIsStillParsedAfterOneIsDropped() {
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(null)

        bleComm.onCharacteristicChanged(framedPacket())
        bleComm.onCharacteristicChanged(framedPacket())

        // Two calls means the first packet was consumed from readBuffer rather than left to block it.
        verify(bleEncryption, times(2)).getDecryptedPacket(any())
    }

    /**
     * The packet must be cut out under the same lock that validated it.
     *
     * The length check and the copy out of `readBuffer` used to sit on opposite sides of
     * `synchronized(readBuffer)`. Two notifications arriving together could then both pass the
     * "is there a whole packet" check, and the second copy ran after the first had already consumed
     * the bytes - with a negative length:
     * "src.length=1024 srcPos=10 dst.length=1024 dstPos=0 length=-10", as reported on 4.0.0-dev-c.
     */
    @Test
    fun concurrentNotificationsNeverCopyANegativeLength() {
        // Decryption must SUCCEED here, or the integrity-check path above would throw first on the old
        // code and hide the race this test is about. An encryption response with an opcode no branch
        // matches is dispatched nowhere, so only the buffer handling is under test.
        whenever(bleEncryption.getDecryptedPacket(any()))
            .thenReturn(byteArrayOf(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toByte(), 0x7F, 0x00))
        val failure = AtomicReference<Throwable?>(null)

        val threads = (1..4).map {
            Thread {
                try {
                    repeat(500) { bleComm.onCharacteristicChanged(framedPacket()) }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertThat(failure.get()).isNull()
    }
}
