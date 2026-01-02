package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TempBasalInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is TempBasalInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.activePlugin = activePlugin
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseTempBasalInfo() {
        // Given - TBR running at 120%, 90 minutes total, 30 minutes elapsed
        val packet = TempBasalInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            tbStatus = 1,      // Running
            tbTime = 6,        // 90 minutes (6 * 15 min)
            tbInjectRateRatio = 50120, // 120% (50000 + 120)
            tbElapsedTime = 30 // 30 minutes elapsed
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.tbStatus).isEqualTo(1)
        assertThat(diaconnG8Pump.tbTime).isEqualTo(6)
        assertThat(diaconnG8Pump.tbInjectRateRatio).isEqualTo(50120)
        assertThat(diaconnG8Pump.tbElapsedTime).isEqualTo(30)
    }

    @Test
    fun handleMessageShouldHandleNoTempBasal() {
        // Given - No TBR running
        val packet = TempBasalInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            tbStatus = 2,      // Not running
            tbTime = 0,
            tbInjectRateRatio = 0,
            tbElapsedTime = 0
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.tbStatus).isEqualTo(2)
        assertThat(diaconnG8Pump.tbTime).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleAbsoluteTempBasal() {
        // Given - Absolute TBR 2.5 U/h = 1250 (1000 + 250)
        val packet = TempBasalInquireResponsePacket(packetInjector)
        val data = createValidPacket(
            tbStatus = 1,
            tbTime = 8,        // 2 hours
            tbInjectRateRatio = 1250, // 2.5 U/h absolute
            tbElapsedTime = 45
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.tbInjectRateRatio).isEqualTo(1250)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = TempBasalInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = TempBasalInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x8A.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = TempBasalInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_TEMP_BASAL_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(
        tbStatus: Int,
        tbTime: Int,
        tbInjectRateRatio: Int,
        tbElapsedTime: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x8A.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = 16.toByte() // result (success)
        data[5] = tbStatus.toByte()
        data[6] = tbTime.toByte()
        data[7] = (tbInjectRateRatio and 0xFF).toByte()
        data[8] = ((tbInjectRateRatio shr 8) and 0xFF).toByte()
        data[9] = (tbElapsedTime and 0xFF).toByte()
        data[10] = ((tbElapsedTime shr 8) and 0xFF).toByte()

        for (i in 11 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x8A.toByte()
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
