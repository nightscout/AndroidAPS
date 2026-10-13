package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InjectionProgressReportPacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is InjectionProgressReportPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.rxBus = rxBus
                it.rh = rh
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldParseBolusProgress() {
        // Given
        val packet = InjectionProgressReportPacket(packetInjector)
        // Bolus: set 5.0U, delivered 2.5U, speed 5, progress 50%
        val data = createValidPacket(
            setAmount = 500,  // 5.0U * 100
            injAmount = 250,   // 2.5U * 100
            speed = 5,
            progress = 50
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.bolusingSetAmount).isWithin(0.01).of(5.0)
        assertThat(diaconnG8Pump.bolusingInjAmount).isWithin(0.01).of(2.5)
        assertThat(diaconnG8Pump.bolusingSpeed).isEqualTo(5)
        assertThat(diaconnG8Pump.bolusingInjProgress).isEqualTo(50)
    }

    @Test
    fun handleMessageShouldParseSmallBolus() {
        // Given
        val packet = InjectionProgressReportPacket(packetInjector)
        // Small bolus: set 0.5U, delivered 0.1U
        val data = createValidPacket(
            setAmount = 50,   // 0.5U * 100
            injAmount = 10,    // 0.1U * 100
            speed = 3,
            progress = 20
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.bolusingSetAmount).isWithin(0.01).of(0.5)
        assertThat(diaconnG8Pump.bolusingInjAmount).isWithin(0.01).of(0.1)
    }

    @Test
    fun handleMessageShouldParseCompletedBolus() {
        // Given
        val packet = InjectionProgressReportPacket(packetInjector)
        // Completed bolus: 100% progress
        val data = createValidPacket(
            setAmount = 1000,  // 10.0U
            injAmount = 1000,  // 10.0U
            speed = 8,
            progress = 100
        )

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.bolusingSetAmount).isWithin(0.01).of(10.0)
        assertThat(diaconnG8Pump.bolusingInjAmount).isWithin(0.01).of(10.0)
        assertThat(diaconnG8Pump.bolusingInjProgress).isEqualTo(100)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = InjectionProgressReportPacket(packetInjector)
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
        val packet = InjectionProgressReportPacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xEA.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = InjectionProgressReportPacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_INJECTION_PROGRESS_REPORT")
    }

    private fun createValidPacket(
        setAmount: Int,
        injAmount: Int,
        speed: Int,
        progress: Int
    ): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xEA.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end

        // setAmount as short (little endian)
        data[4] = (setAmount and 0xFF).toByte()
        data[5] = ((setAmount shr 8) and 0xFF).toByte()

        // injAmount as short (little endian)
        data[6] = (injAmount and 0xFF).toByte()
        data[7] = ((injAmount shr 8) and 0xFF).toByte()

        data[8] = speed.toByte()
        data[9] = progress.toByte()

        // Fill rest with padding
        for (i in 10 until 19) {
            data[i] = 0xff.toByte()
        }

        // Calculate and set CRC
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        return data
    }
}
