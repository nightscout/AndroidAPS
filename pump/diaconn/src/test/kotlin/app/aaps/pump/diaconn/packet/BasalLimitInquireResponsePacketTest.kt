package app.aaps.pump.diaconn.packet

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class BasalLimitInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BasalLimitInquireResponsePacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
                it.diaconnG8Pump = diaconnG8Pump
                it.preferences = preferences
                it.rh = rh
            }
        }
    }

    @BeforeEach
    fun setup() {
        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
    }

    @Test
    fun handleMessageShouldProcessValidResponse() {
        // Given - maxBasalPerHours = 3.5 U/h (350 / 100.0)
        // For firmware < 3.0, maxBasal = maxBasalPerHours * 2.0 = 7.0
        `when`(preferences.get(DiaconnStringNonKey.PumpVersion)).thenReturn("2.63")

        val packet = BasalLimitInquireResponsePacket(packetInjector)

        // Valid packet: SOP(0xef) + TYPE(0x92) + SEQ + CON_END + result(16=success) + maxBasalPerHours(350 as short) + padding + CRC
        val data = createValidPacket(result = 16, maxBasalPerHours = 350)

        // When
        packet.handleMessage(data)
2
        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.maxBasalPerHours).isWithin(0.01).of(3.5)
        assertThat(diaconnG8Pump.maxBasal).isWithin(0.01).of(7.0)
    }

    @Test
    fun handleMessageShouldUseHigherMultiplierForNewerFirmware() {
        // Given - For firmware >= 3.0, maxBasal = maxBasalPerHours * 2.5
        `when`(preferences.get(DiaconnStringNonKey.PumpVersion)).thenReturn("3.53")

        val packet = BasalLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(result = 16, maxBasalPerHours = 400) // 4.0 U/h

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.maxBasalPerHours).isWithin(0.01).of(4.0)
        assertThat(diaconnG8Pump.maxBasal).isWithin(0.01).of(10.0) // 4.0 * 2.5
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        `when`(preferences.get(DiaconnStringNonKey.PumpVersion)).thenReturn("2.63")

        val packet = BasalLimitInquireResponsePacket(packetInjector)
        val data = createValidPacket(result = 17, maxBasalPerHours = 350) // 17 = CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BasalLimitInquireResponsePacket(packetInjector)

        // Invalid packet with wrong SOP
        val data = ByteArray(20)
        data[0] = 0x00 // Wrong SOP (should be 0xef)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BasalLimitInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BASAL_LIMIT_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(result: Int, maxBasalPerHours: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0x92.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = result.toByte() // result code
        data[5] = (maxBasalPerHours and 0xFF).toByte() // maxBasalPerHours low byte
        data[6] = ((maxBasalPerHours shr 8) and 0xFF).toByte() // maxBasalPerHours high byte

        // Fill rest with padding
        for (i in 7 until 19) {
            data[i] = 0xff.toByte()
        }

        // Calculate and set CRC
        data[19] = DiaconnG8Packet.getCRC(data, 19)

        return data
    }
}
