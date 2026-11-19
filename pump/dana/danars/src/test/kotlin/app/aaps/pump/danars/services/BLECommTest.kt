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
import app.aaps.pump.dana.R
import app.aaps.pump.dana.keys.DanaStringComposedKey
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
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
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

    private companion object {

        private const val ADDRESS = "00:11:22:33:44:55"
        private const val START = 1_000_000L
    }

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
            dateUtil,
            preferences,
            configBuilder,
            notificationManager,
            bleTransport,
            DanaRSAlarmReporter(rh, notificationManager, pumpSync, danaPump)
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

    /**
     * The length byte must be read unsigned. A Dana-i2 bulk history packet is up to 202 bytes,
     * read as a signed byte that length was negative and the packet could not be parsed.
     */
    @Test
    fun packetLongerThan127BytesIsParsed() {
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(null)
        val length = 202
        val longPacket = ByteArray(length + 7).apply {
            this[0] = 0xA5.toByte()
            this[1] = 0xA5.toByte()
            this[2] = length.toByte()
            this[length + 5] = 0x5A.toByte()
            this[length + 6] = 0x5A.toByte()
        }

        // BLE delivers it in parts of 20 bytes
        longPacket.toList().chunked(20).forEach { bleComm.onCharacteristicChanged(it.toByteArray()) }

        verify(bleEncryption, times(1)).getDecryptedPacket(any())
    }

    /**
     * A pump with easy mode (Dana-i2 0x0C) gets the easy menu check (0xF4) after the pump check.
     * Its reply sets the easy mode and basal unit, then the handshake continues with the BLE5 time info.
     */
    @Test
    fun easyModePumpIsCheckedBeforeTheBle5TimeInfo() {
        whenever(danaPump.hwModel).thenReturn(0x0C)
        whenever(preferences.get(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>())).thenReturn("982316")
        whenever(bleEncryption.getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())).thenReturn(byteArrayOf(0))
        val ok = "OK".encodeToByteArray()
        // Real reply of the Korean Dana-i2: OK M 0C P 30 + pairing key
        val pumpCheckReply = byteArrayOf(0x02, 0x00) + ok + byteArrayOf('M'.code.toByte(), 0x0C, 'P'.code.toByte(), 0x30) + "982316".encodeToByteArray()
        val easyMenuReply = byteArrayOf(0x02, BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK.toByte(), 0x01, 0x00)
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(pumpCheckReply, easyMenuReply)

        bleComm.onCharacteristicChanged(framedPacket())
        verify(bleEncryption).getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK, null, null)
        verify(bleEncryption, never()).getEncryptedPacket(eq(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION), anyOrNull(), anyOrNull())

        bleComm.onCharacteristicChanged(framedPacket())
        verify(danaPump).isEasyModeEnabled = true
        verify(danaPump, times(2)).isConfigUD = false // reset on the pump check, then the reply (U/h)
        verify(bleEncryption).getEncryptedPacket(eq(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION), anyOrNull(), anyOrNull())
    }

    /** Decrypted "PUMP" reply to the pump check, optionally with the Dana-i2 error byte */
    private fun pumpReply(vararg errorByte: Int): ByteArray =
        byteArrayOf(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toByte(), BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK.toByte()) +
            "PUMP".encodeToByteArray() + errorByte.map { it.toByte() }.toByteArray()

    /**
     * Dana-i2 adds 1 error byte to the "PUMP" reply. This 7 byte reply used to miss the "PUMP" check
     * and fall into the wrong serial branch, which cleared the pairing and hid the real pump error.
     */
    @Test
    fun danaI2PumpErrorShowsTheErrorAndKeepsThePairing() {
        whenever(rh.gs(R.string.occlusion)).thenReturn("Occlusion")
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(pumpReply(0x02))

        bleComm.onCharacteristicChanged(framedPacket())

        verify(bleTransport).updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = "Occlusion"))
        verify(danaRSPlugin, never()).clearPairing()
    }

    @Test
    fun olderPumpErrorWithoutErrorByteStillShowsPumpError() {
        whenever(rh.gs(R.string.pumperror)).thenReturn("Pump Error")
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(pumpReply())

        bleComm.onCharacteristicChanged(framedPacket())

        verify(bleTransport).updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = "Pump Error"))
        verify(danaRSPlugin, never()).clearPairing()
    }

    /** The empty answer `02 00` to the pump check, as a real Dana-i2 gives it after an unfinished handshake (#5571). */
    private val unknownAnswer = byteArrayOf(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toByte(), BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK.toByte())

    /** A v1 OK answer to the pump check */
    private val okAnswer = unknownAnswer + "OK".encodeToByteArray()

    /** The pump sends [answer] at [time]. Then the link drops, as after a real connection, unless [linkStays]. */
    private fun pumpAnswersAt(time: Long, answer: ByteArray, linkStays: Boolean = false) {
        whenever(dateUtil.now()).thenReturn(time)
        whenever(bleEncryption.getDecryptedPacket(any())).thenReturn(answer)
        bleComm.onCharacteristicChanged(framedPacket())
        if (!linkStays) bleComm.onConnectionStateChanged(false)
    }

    @Test
    fun unknownPumpCheckAnswerClosesTheLinkAndKeepsThePairing() {
        pumpAnswersAt(START, unknownAnswer)

        verify(bleGatt).disconnect()
        verify(danaRSPlugin, never()).clearPairing()
        verify(bleTransport).updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = "test"))
    }

    @Test
    fun theQueueWaitsDuringThePauseAfterAnUnknownAnswer() {
        pumpAnswersAt(START, unknownAnswer)
        // The link is down, the pause keeps the queue from connecting
        bleComm.onConnectionStateChanged(false)

        whenever(dateUtil.now()).thenReturn(START + BLEComm.UNKNOWN_ANSWER_PAUSE_MS - 1)
        assertThat(bleComm.isConnecting).isTrue()
        whenever(dateUtil.now()).thenReturn(START + BLEComm.UNKNOWN_ANSWER_PAUSE_MS)
        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun noMessageIsSentDuringThePause() {
        pumpAnswersAt(START, unknownAnswer)
        bleComm.onConnectionStateChanged(false)
        whenever(danaRSPacket.friendlyName).thenReturn("TestPacket")

        bleComm.sendMessage(danaRSPacket)

        verify(bleEncryption, never()).getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())
    }

    @Test
    fun aConnectionAskedForEndsThePause() {
        `when`(bleAdapter.getDeviceName(ADDRESS)).thenReturn("Dana")
        `when`(bleAdapter.isDeviceBonded(ADDRESS)).thenReturn(true)
        `when`(bleGatt.connect(ADDRESS)).thenReturn(true)
        pumpAnswersAt(START, unknownAnswer)

        // The pair wizard connects at once, and when that link drops the queue may connect again
        bleComm.connect("test", ADDRESS)
        bleComm.onConnectionStateChanged(false)

        assertThat(bleComm.isConnecting).isFalse()
    }

    @Test
    fun threeUnknownAnswersInLessThan5MinutesKeepThePairing() {
        pumpAnswersAt(START, unknownAnswer)
        pumpAnswersAt(START + 2 * 60_000L, unknownAnswer)
        pumpAnswersAt(START + 4 * 60_000L, unknownAnswer)

        verify(danaRSPlugin, never()).clearPairing()
    }

    @Test
    fun twoUnknownAnswersOver5MinutesKeepThePairing() {
        pumpAnswersAt(START, unknownAnswer)
        pumpAnswersAt(START + BLEComm.UNKNOWN_ANSWER_CLEAR_AFTER_MS + 60_000L, unknownAnswer)

        verify(danaRSPlugin, never()).clearPairing()
    }

    @Test
    fun unknownAnswersThatGoOnClearThePairing() {
        pumpAnswersAt(START, unknownAnswer)
        pumpAnswersAt(START + 2 * 60_000L, unknownAnswer)
        pumpAnswersAt(START + 4 * 60_000L, unknownAnswer)
        pumpAnswersAt(START + BLEComm.UNKNOWN_ANSWER_CLEAR_AFTER_MS, unknownAnswer)

        verify(danaRSPlugin, times(1)).clearPairing()
    }

    @Test
    fun anOkAnswerStartsTheCountAgain() {
        whenever(preferences.get(eq(DanaStringComposedKey.ParingKey), anyVararg<Any>())).thenReturn("")
        whenever(bleEncryption.getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())).thenReturn(byteArrayOf(0))
        pumpAnswersAt(START, unknownAnswer)
        pumpAnswersAt(START + 2 * 60_000L, unknownAnswer)
        pumpAnswersAt(START + 3 * 60_000L, okAnswer)
        pumpAnswersAt(START + 4 * 60_000L, unknownAnswer)
        pumpAnswersAt(START + BLEComm.UNKNOWN_ANSWER_CLEAR_AFTER_MS + 60_000L, unknownAnswer)

        verify(danaRSPlugin, never()).clearPairing()
    }

    /** A BLE5 OK answer of a Dana-i2 (model 0x0B) carrying [key] */
    private fun ble5OkAnswer(key: String): ByteArray =
        okAnswer + byteArrayOf('M'.code.toByte(), 0x0B, 'P'.code.toByte(), 0x30) + key.encodeToByteArray()

    /**
     * Seen on a real Dana-i2: AAPS connected again 0.6 s after a disconnect, Android kept the radio link, and the pump
     * got two pump checks on one link. It answered OK to the first, then `02 00` to the second, and then went on
     * with the handshake. Closing the link on the `02 00` threw away a connection that worked.
     */
    @Test
    fun anUnknownAnswerAfterAnOkOnTheSameConnectionIsIgnored() {
        whenever(danaPump.hwModel).thenReturn(0x0B)
        whenever(preferences.get(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>())).thenReturn("982316")
        whenever(bleEncryption.getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())).thenReturn(byteArrayOf(0))

        pumpAnswersAt(START, ble5OkAnswer("982316"), linkStays = true)
        pumpAnswersAt(START + 57, unknownAnswer, linkStays = true)

        verify(bleGatt, never()).disconnect()
        verify(danaRSPlugin, never()).clearPairing()
        verify(bleTransport, never()).updatePairingState(PairingState(step = PairingStep.ERROR, errorMessage = "test"))
        assertThat(bleComm.isConnecting).isFalse()
        // The handshake went on once, from the OK
        verify(bleEncryption, times(1)).getEncryptedPacket(eq(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION), anyOrNull(), anyOrNull())
    }

    @Test
    fun onANewConnectionAnUnknownAnswerClosesTheLinkAgain() {
        whenever(danaPump.hwModel).thenReturn(0x0B)
        whenever(preferences.get(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>())).thenReturn("982316")
        whenever(bleEncryption.getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())).thenReturn(byteArrayOf(0))

        pumpAnswersAt(START, ble5OkAnswer("982316"))
        pumpAnswersAt(START + 60_000L, unknownAnswer)

        verify(bleGatt).disconnect()
    }

    /** A key that is not 6 digits made BleEncryption.setBle5Key throw inside the Bluetooth callback, and the handshake stopped with no message */
    @Test
    fun aStoredBle5KeyThatIsNotDigitsIsNotUsed() {
        whenever(danaPump.hwModel).thenReturn(0x0B)
        whenever(preferences.get(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>())).thenReturn("12345Z")
        `when`(bleAdapter.getDeviceName(ADDRESS)).thenReturn("Dana")
        `when`(bleAdapter.isDeviceBonded(ADDRESS)).thenReturn(true)
        `when`(bleGatt.connect(ADDRESS)).thenReturn(true)
        bleComm.connect("test", ADDRESS)

        // No key in the answer (byte 8 is 0), so the stored one would be used
        pumpAnswersAt(START, ble5OkAnswer("\u0000\u0000\u0000\u0000\u0000\u0000"))

        verify(bleEncryption, never()).setBle5Key(any())
        verify(bleAdapter).removeBond(ADDRESS)
        verify(bleGatt).disconnect()
    }

    @Test
    fun aBle5KeyFromThePumpThatIsNotDigitsIsNotStored() {
        whenever(danaPump.hwModel).thenReturn(0x0B)
        whenever(preferences.get(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>())).thenReturn("982316")
        whenever(bleEncryption.getEncryptedPacket(anyInt(), anyOrNull(), anyOrNull())).thenReturn(byteArrayOf(0))

        pumpAnswersAt(START, ble5OkAnswer("ZZZZZZ"))

        verify(preferences, never()).put(eq(DanaStringComposedKey.Ble5PairingKey), anyVararg<Any>(), value = eq("ZZZZZZ"))
        verify(bleEncryption).setBle5Key("982316".encodeToByteArray())
    }
}
