package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class AuthorizePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketAndSNWhenCalledThenReturnAuthorizePacket() {
        // Inputs
        val opCode = 5
        val sn = 2859923929

        // Call
        val packet = AuthorizePacket(packetInjector, sn)
        val result = packet.getRequest()

        // Expected values
        val key = 3364239851
        val type = 2
        val expectedByteArray = byteArrayOf(opCode.toByte()) + type.toByte() + 0.toByteArray(4) + key.toByteArray(4)
        assertEquals(10, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
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
        val packet = AuthorizePacket(packetInjector, 0)
        val result = packet.handleResponse(response)

        // Expected values
        val swString = "$swVerX.$swVerY.$swVerZ"
        assertEquals(true, result)
        assertEquals(false, packet.failed)
        assertEquals(deviceType, packet.deviceType)
        assertEquals(swString, packet.swVersion)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 5
        val responseCode = 0

        // Call
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)
        val packet = AuthorizePacket(packetInjector, 0)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertEquals(false, result)
        assertEquals(true, packet.failed)
    }
}
