package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.api.DiaconnLogUploader
import app.aaps.pump.diaconn.database.DiaconnHistoryRecordDao
import app.aaps.pump.diaconn.keys.DiaconnBooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer

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
        // The per-log branches call rh.gs(<diaconn string>) (non-null String) and dateUtil date-string helpers.
        // The base leaves single-arg rh.gs unstubbed (→ null → Kotlin non-null NPE) and dateUtil is a real spy
        // whose android formatters can misbehave off-device. Stub both so the branches run to completion.
        whenever(rh.gs(anyInt())).thenReturn("x")
        doReturn("t").whenever(dateUtil).timeString(any())
        doReturn("dt").whenever(dateUtil).dateAndTimeString(any())
        // Every pumpSync.* method the branches call is `suspend fun … : Boolean`; suspend funcs return Object at
        // the JVM level, so a bare mock yields null and the `if (!newRecord)` unbox NPEs. A blanket false answer
        // covers them all (all pumpSync calls here return Boolean) without stubbing each defaulted signature.
        pumpSync = mock(defaultAnswer = Answer { false })
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

    // Every LOG_KIND handled by the big when(pumpLogKind) block. Value = the kind byte placed at logData[4].
    private val allLogKinds: List<Pair<String, Byte>> = listOf(
        "ResetSysV3" to 0x01, "SuspendV2" to 0x03, "SuspendRelease" to 0x04,
        "MealSuccess" to 0x08, "MealFail" to 0x09, "NormalSuccess" to 0x0A, "NormalFail" to 0x0B,
        "SetSquare" to 0x0C, "SquareSuccess" to 0x0D, "SquareFail" to 0x0E, "SetDual" to 0x0F,
        "DualSuccess" to 0x10, "DualFail" to 0x11, "TbStart" to 0x12, "TbStop" to 0x13,
        "ChangeTube" to 0x18, "ChangeInjector" to 0x1A, "ChangeNeedle" to 0x1C,
        "AlarmBattery" to 0x28, "AlarmBlock" to 0x29, "AlarmShortAge" to 0x2A,
        "1HourBasal" to 0x2C, "1DayBasal" to 0x2E, "1Day" to 0x2F, "DualNormal" to 0x35
    )

    @Test
    fun handleMessageProcessesEveryLogKindWithoutError() {
        // Enabling these makes the change/reset branches also fire the therapy-event insert sub-paths.
        whenever(preferences.get(DiaconnBooleanKey.LogInsulinChange)).thenReturn(true)
        whenever(preferences.get(DiaconnBooleanKey.LogTubeChange)).thenReturn(true)
        whenever(preferences.get(DiaconnBooleanKey.LogCannulaChange)).thenReturn(true)
        whenever(preferences.get(DiaconnBooleanKey.LogBatteryChange)).thenReturn(true)

        for ((label, kind) in allLogKinds) {
            val packet = BigLogInquireResponsePacket(packetInjector)
            packet.handleMessage(bigLogPacket(listOf(0 to kind)))
            assertThat(packet.failed).isFalse()
            assertThat(label).isNotEmpty() // keeps the label referenced for a readable failure
        }
    }

    @Test
    fun handleMessageProcessesMultipleLogsInOnePacketAndTracksProgress() {
        val entries = listOf(
            1 to 0x0A.toByte(),  // normal bolus
            2 to 0x12.toByte(),  // temp basal start
            3 to 0x2F.toByte(),  // daily bolus
            4 to 0x28.toByte(),  // battery alarm
            5 to 0x03.toByte()   // suspend
        )
        val packet = BigLogInquireResponsePacket(packetInjector)

        packet.handleMessage(bigLogPacket(entries))

        assertThat(packet.failed).isFalse()
        // Each processed record updates the pump's sync cursor to the latest log number / wrapping count.
        assertThat(diaconnG8Pump.apslastLogNum).isEqualTo(5)
        assertThat(diaconnG8Pump.apsWrappingCount).isEqualTo(7)
    }

    @Test
    fun handleMessageIgnoresUnknownLogKind() {
        // 0x3F is not a handled kind → the else arm emits an in-progress status and continues.
        val packet = BigLogInquireResponsePacket(packetInjector)

        packet.handleMessage(bigLogPacket(listOf(0 to 0x3F.toByte())))

        assertThat(packet.failed).isFalse()
    }

    /**
     * Build a SOP_BIG (0xed) 182-byte packet carrying [entries] as (logNum, logKind) log records.
     * Layout: [ed][b2][seq][conEnd][result=16][logLength] then 15-byte records
     * (wrapping:1, logNum:2 LE, logData:12) with logData[4]=kind (rest zero → 1970 timestamp), CRC last.
     */
    private fun bigLogPacket(entries: List<Pair<Int, Byte>>): ByteArray {
        val data = ByteArray(DiaconnG8Packet.MSG_LEN_BIG) // 182 bytes required for a 0xed packet
        data[0] = DiaconnG8Packet.SOP_BIG
        data[1] = 0xb2.toByte()
        data[2] = 0x01
        data[3] = 0x00
        data[4] = 16          // result: success
        data[5] = entries.size.toByte()
        var off = 6
        for ((logNum, kind) in entries) {
            data[off] = 7                                       // wrappingCount
            data[off + 1] = (logNum and 0xff).toByte()          // logNum low byte
            data[off + 2] = ((logNum shr 8) and 0xff).toByte()  // logNum high byte
            data[off + 3 + 4] = kind                            // logData[4] = kind
            off += 15
        }
        data[data.size - 1] = DiaconnG8Packet.getCRC(data, data.size - 1)
        return data
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
