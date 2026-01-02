package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.api.DiaconnLogUploader
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class BigLogInquireResponsePacketTest : TestBaseWithProfile() {

    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var diaconnHistoryRecordDao: DiaconnHistoryRecordDao
    @Mock lateinit var diaconnLogUploader: DiaconnLogUploader

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BigLogInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.rxBus = rxBus
                it.rh = rh
                it.activePlugin = activePlugin
                it.diaconnG8Pump = diaconnG8Pump
                it.detailedBolusInfoStorage = detailedBolusInfoStorage
                it.temporaryBasalStorage = temporaryBasalStorage
                it.preferences = preferences
                it.pumpSync = pumpSync
                it.diaconnHistoryRecordDao = diaconnHistoryRecordDao
                it.diaconnLogUploader = diaconnLogUploader
                it.context = context
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldProcessValidResponse() {
        // Given - Valid response packet with 0 logs
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createValidPacket(logCount = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = ByteArray(50)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnParameterError() {
        // Given - Result code 18 indicates parameter error
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(18)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnSystemError() {
        // Given - Any result code other than 16 that's not 17-19 is system error
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(20)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnInvalidCrc() {
        // Given - Valid packet structure but invalid CRC
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createValidPacket(0)
        data[19] = 0xFF.toByte() // Corrupt CRC

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnInvalidPacketSize() {
        // Given - Packet size that's not 20 or 182 bytes
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = ByteArray(25)
        data[0] = 0xef.toByte() // Valid SOP
        data[1] = 0xb2.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = 16.toByte() // Valid result
        data[24] = DiaconnG8Packet.getCRC(data, 24)

        // When
        packet.handleMessage(data)

        // Then - Should fail due to invalid packet size
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnInvalidSopByte() {
        // Given - Invalid start-of-packet byte
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = ByteArray(20)
        data[0] = 0xaa.toByte() // Invalid SOP (should be 0xef or 0xed)
        data[1] = 0xb2.toByte()
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldHandleZeroResult() {
        // Given - Result code 0 (system error)
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldHandleMaxResult() {
        // Given - Maximum byte value result code
        val packet = BigLogInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(255)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    private fun createValidPacket(logCount: Int): ByteArray {
        val baseSize = 20
        val logSize = 15 // Each log entry is 15 bytes (1 wrapping + 2 logNum + 12 logData)
        val totalSize = baseSize + (logCount * logSize)
        val data = ByteArray(totalSize)

        data[0] = 0xef.toByte() // SOP
        data[1] = 0xb2.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = logCount.toByte() // log count

        // Fill remaining data
        for (i in 6 until totalSize - 1) {
            data[i] = 0x00.toByte()
        }

        data[totalSize - 1] = DiaconnG8Packet.getCRC(data, totalSize - 1)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xb2.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()

        for (i in 5 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }
}
