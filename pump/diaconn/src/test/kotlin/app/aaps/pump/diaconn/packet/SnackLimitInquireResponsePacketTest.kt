package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SnackLimitInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SnackLimitInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseBolusLimits() {
        // Given - maxBolus 10.0U, maxBolusPerDay 50.0U
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(maxBolus = 1000, maxBolusPerDay = 5000)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.maxBolus).isWithin(0.01).of(10.0)
        assertThat(diaconnG8Pump.maxBolusePerDay).isWithin(0.01).of(50.0)
    }

    @Test
    fun handleMessageShouldHandleMinimumLimits() {
        // Given - Minimum limits (0.5U bolus, 5.0U per day)
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(maxBolus = 50, maxBolusPerDay = 500)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.maxBolus).isWithin(0.01).of(0.5)
        assertThat(diaconnG8Pump.maxBolusePerDay).isWithin(0.01).of(5.0)
    }

    @Test
    fun handleMessageShouldHandleMaximumLimits() {
        // Given - Maximum limits (25.0U bolus, 200.0U per day)
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(maxBolus = 2500, maxBolusPerDay = 20000)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.maxBolus).isWithin(0.01).of(25.0)
        assertThat(diaconnG8Pump.maxBolusePerDay).isWithin(0.01).of(200.0)
    }

    @Test
    fun handleMessageShouldHandleTypicalLimits() {
        // Given - Typical limits (15.0U bolus, 100.0U per day)
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(maxBolus = 1500, maxBolusPerDay = 10000)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.maxBolus).isWithin(0.01).of(15.0)
        assertThat(diaconnG8Pump.maxBolusePerDay).isWithin(0.01).of(100.0)
    }

    @Test
    fun handleMessageShouldHandleDecimalValues() {
        // Given - Decimal values (7.55U bolus, 38.75U per day)
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(maxBolus = 755, maxBolusPerDay = 3875)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.maxBolus).isWithin(0.01).of(7.55)
        assertThat(diaconnG8Pump.maxBolusePerDay).isWithin(0.01).of(38.75)
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = SnackLimitInquireResponsePacket(packetInjector)
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = SnackLimitInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x90.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = SnackLimitInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_SNACK_LIMIT_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(maxBolus: Int, maxBolusPerDay: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x90.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = (maxBolus and 0xFF).toByte() // maxBolus low byte
        data[6] = ((maxBolus shr 8) and 0xFF).toByte() // maxBolus high byte
        data[7] = (maxBolusPerDay and 0xFF).toByte() // maxBolusPerDay low byte
        data[8] = ((maxBolusPerDay shr 8) and 0xFF).toByte() // maxBolusPerDay high byte

        for (i in 9 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0x90.toByte()
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
