package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class GetDeviceTypePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 6

        // Call
        val packet = GetDeviceTypePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // Inputs
        val opCode = 6
        val responseCode = 0
        val deviceType = 80
        val deviceSN: Long = 12345678
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + deviceType.toByte() + deviceSN.toByteArray(4)

        // Call       
        val packet = GetDeviceTypePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertEquals(true, result)
        assertEquals(false, packet.failed)
        assertEquals(deviceType, packet.deviceType)
        assertEquals(deviceSN, packet.deviceSN)

    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 6
        val responseCode = 0
        val deviceType = 80
        val deviceSN: Long = 12345678
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + deviceType.toByte() + deviceSN.toByteArray(4)

        // Call
        val packet = GetDeviceTypePacket(packetInjector)
        val result = packet.handleResponse(response.sliceArray(0..response.size - 2))

        // Expected values
        assertEquals(false, result)
        assertEquals(true, packet.failed)
        assertEquals(0, packet.deviceType)
        assertEquals(0, packet.deviceSN)
    }
}
