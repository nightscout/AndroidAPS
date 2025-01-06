package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test

class AuthorizePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AuthorizePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketAndSNWhenCalledThenReturnAuthorizePacket() {
        // Inputs
        val opCode = 5
        val _pumpSN = MedtrumPump::class.java.getDeclaredField("_pumpSN")
        _pumpSN.isAccessible = true
        _pumpSN.setLong(medtrumPump, 2859923929)
        medtrumPump.patchSessionToken = 667

        // Call
        val packet = AuthorizePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val key = 3364239851
        val type = 2
        val expectedByteArray = byteArrayOf(opCode.toByte()) + type.toByte() + medtrumPump.patchSessionToken.toByteArray(4) + key.toByteArray(4)
        assertThat(result).asList().containsExactlyElementsIn(expectedByteArray.toList()).inOrder()
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // Inputs
        val opCode = 5
        val responseCode = 0
        val deviceType = 80
        val swVerX = 12
        val swVerY = 1
        val swVerZ = 3

        // Call
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + 0.toByte() + deviceType.toByte() + swVerX.toByte() + swVerY.toByte() + swVerZ.toByte()
        val packet = AuthorizePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        val swString = "$swVerX.$swVerY.$swVerZ"
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(medtrumPump.deviceType).isEqualTo(deviceType)
        assertThat(medtrumPump.swVersion).isEqualTo(swString)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 5
        val responseCode = 0

        // Call
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)
        val packet = AuthorizePacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }
}
