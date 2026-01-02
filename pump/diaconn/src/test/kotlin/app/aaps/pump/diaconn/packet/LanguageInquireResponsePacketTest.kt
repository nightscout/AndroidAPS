package app.aaps.pump.diaconn.packet

import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LanguageInquireResponsePacketTest : TestBaseWithProfile() {

    private lateinit var diaconnG8Pump: DiaconnG8Pump

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is LanguageInquireResponsePacket) {
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
    fun handleMessageShouldParseLanguageSetting() {
        // Given - Korean language (value 1)
        val packet = LanguageInquireResponsePacket(packetInjector)
        val data = createValidPacket(language = 1)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isFalse()
        assertThat(diaconnG8Pump.selectedLanguage).isEqualTo(1)
    }

    @Test
    fun handleMessageShouldHandleEnglishLanguage() {
        // Given - English language (value 0)
        val packet = LanguageInquireResponsePacket(packetInjector)
        val data = createValidPacket(language = 0)

        // When
        packet.handleMessage(data)

        // Then
        assertThat(diaconnG8Pump.selectedLanguage).isEqualTo(0)
    }

    @Test
    fun handleMessageShouldHandleOtherLanguages() {
        // Given - Test different language codes
        val packet = LanguageInquireResponsePacket(packetInjector)

        for (langCode in 0..5) {
            val data = createValidPacket(language = langCode)
            packet.handleMessage(data)
            assertThat(diaconnG8Pump.selectedLanguage).isEqualTo(langCode)
        }
    }

    @Test
    fun handleMessageShouldFailOnInvalidResult() {
        // Given
        val packet = LanguageInquireResponsePacket(packetInjector)
        val data = createPacketWithResult(17) // CRC error

        // When
        packet.handleMessage(data)

        // Then
        assertThat(packet.failed).isTrue()
    }

    @Test
    fun handleMessageShouldFailOnDefectivePacket() {
        // Given
        val packet = LanguageInquireResponsePacket(packetInjector)
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
        val packet = LanguageInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.msgType).isEqualTo(0xA0.toByte())
    }

    @Test
    fun friendlyNameShouldBeCorrect() {
        // Given
        val packet = LanguageInquireResponsePacket(packetInjector)

        // Then
        assertThat(packet.friendlyName).isEqualTo("PUMP_LANGUAGE_INQUIRE_RESPONSE")
    }

    private fun createValidPacket(language: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte() // SOP
        data[1] = 0xA0.toByte() // msgType
        data[2] = 0x01.toByte() // seq
        data[3] = 0x00.toByte() // con_end
        data[4] = 16.toByte()   // result (success)
        data[5] = language.toByte()

        for (i in 6 until 19) {
            data[i] = 0xff.toByte()
        }

        data[19] = DiaconnG8Packet.getCRC(data, 19)
        return data
    }

    private fun createPacketWithResult(result: Int): ByteArray {
        val data = ByteArray(20)
        data[0] = 0xef.toByte()
        data[1] = 0xA0.toByte()
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
