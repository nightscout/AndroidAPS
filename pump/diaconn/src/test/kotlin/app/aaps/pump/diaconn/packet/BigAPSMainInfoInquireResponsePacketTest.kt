package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class BigAPSMainInfoInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BigAPSMainInfoInquireResponsePacket) {
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
        // Mock preferences to return pump version (packet will save "2.63" and then retrieve it)
        `when`(preferences.get(DiaconnStringNonKey.PumpVersion)).thenReturn("2.63")
    }

    @Test
    fun handleMessageShouldProcessValidResponse() {
        // Given - Valid response packet with basic pump info
        val packet = BigAPSMainInfoInquireResponsePacket(packetInjector)
        val data = createValidPacket()

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        // Verify basic fields are parsed
        assertThat(diaconnG8Pump.systemRemainInsulin).isAtLeast(0.0)
        assertThat(diaconnG8Pump.systemRemainBattery).isAtLeast(0)
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = BigAPSMainInfoInquireResponsePacket(packetInjector)
        val data = ByteArray(200)
        data[0] = 0x00 // Wrong SOP

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = BigAPSMainInfoInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // Invalid result

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun msgTypeShouldBeCorrect() {
        // Given
        val packet = BigAPSMainInfoInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0x94.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = BigAPSMainInfoInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_BIG_APS_MAIN_INFO_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(): ByteArray {
        val data = ByteArray(182)  // BIG packet size
        data[0] = 0xed.toByte()    // SOP_BIG (not regular SOP)
        data[1] = 0x94.toByte()    // msgType
        data[2] = 0x01.toByte()    // seq
        data[3] = 0x00.toByte()    // con_end
        data[4] = 16.toByte()      // result (success)

        var pos = 5
        // 1. pump system setting info
        // Insulin remain (2 bytes) = 15000 (150.00 U) - LITTLE_ENDIAN
        data[pos++] = 0x98.toByte()  // Low byte  (15000 = 0x3A98)
        data[pos++] = 0x3A.toByte()  // High byte
        data[pos++] = 80.toByte()    // Battery remain = 80%
        data[pos++] = 1.toByte()     // Base pattern = 1 (basic)
        data[pos++] = 2.toByte()     // systemTbStatus = 2 (released)
        data[pos++] = 2.toByte()     // systemInjectionMealStatus = 2 (not injecting)
        data[pos++] = 2.toByte()     // systemInjectionSnackStatus = 2
        data[pos++] = 2.toByte()     // systemInjectionSquareStatue = 2
        data[pos++] = 2.toByte()     // systemInjectionDualStatus = 2

        // 2. basal injection suspend status
        data[pos++] = 2.toByte()     // basePauseStatus = 2 (released)

        // 3. Pump time
        data[pos++] = 24.toByte()    // year = 2024
        data[pos++] = 1.toByte()     // month = 1
        data[pos++] = 1.toByte()     // day = 1
        data[pos++] = 12.toByte()    // hour = 12
        data[pos++] = 0.toByte()     // minute = 0
        data[pos++] = 0.toByte()     // second = 0

        // 4. pump system info (ASCII values)
        data[pos++] = '1'.code.toByte()  // country (ASCII '1' = 0x31)
        data[pos++] = '1'.code.toByte()  // productType (ASCII '1' = 0x31)
        data[pos++] = 24.toByte()    // makeYear
        data[pos++] = 1.toByte()     // makeMonth
        data[pos++] = 1.toByte()     // makeDay
        data[pos++] = 1.toByte()     // lotNo
        data[pos++] = 0x01.toByte()  // serialNo low byte
        data[pos++] = 0x00.toByte()  // serialNo high byte
        data[pos++] = 2.toByte()     // majorVersion
        data[pos++] = 63.toByte()    // minorVersion

        // Fill rest with reasonable defaults
        for (i in pos until 181) {
            data[i] = 0x00.toByte()
        }

        data[181] = DiaconnG8Packet.getCRC(data, 181)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(182)  // BIG packet size
        data[0] = 0xed.toByte()    // SOP_BIG
        data[1] = 0x94.toByte()
        data[2] = 0x01.toByte()
        data[3] = 0x00.toByte()
        data[4] = result.toByte()

        for (i in 5 until 181) {
            data[i] = 0xff.toByte()
        }

        data[181] = DiaconnG8Packet.getCRC(data, 181)
        return data
    }
}
