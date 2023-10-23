package info.nightscout.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
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
        assertThat(result).asList().containsExactly(opCode.toByte())
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
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(packet.deviceType).isEqualTo(deviceType)
        assertThat(packet.deviceSN).isEqualTo(deviceSN)

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
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
        assertThat(packet.deviceType).isEqualTo(0)
        assertThat(packet.deviceSN).isEqualTo(0)
    }
}
