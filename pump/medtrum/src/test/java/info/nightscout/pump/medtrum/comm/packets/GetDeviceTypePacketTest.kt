package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

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
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(opCode.toByte(), result[0])
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
        Assertions.assertEquals(true, result)
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals(deviceType, packet.deviceType)
        Assertions.assertEquals(deviceSN, packet.deviceSN)

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
        Assertions.assertEquals(false, result)
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals(0, packet.deviceType)
        Assertions.assertEquals(0, packet.deviceSN)
    }
}
