package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test

class MedtrumPacketTest : MedtrumTestBase() {

    /** Test base behavior of the medtrum packet  */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedtrumPacket) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 1

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.getRequest()

        // Expected values
        assertThat(result).asList().containsExactly(opCode.toByte())
    }

    @Test fun handleResponseGivenResponseWhenOpcodeIsCorrectThenResultTrue() {
        // Inputs
        val opCode = 1
        val responseCode = 0
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call        
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenResponseWhenOpcodeIsIncorrectThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 0
        val response = byteArrayOf(0) + (opCode + 1).toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }

    @Test fun handleResponseGivenResponseWhenResponseCodeIsWaitingThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 16384
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0 + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isFalse()
    }

    @Test fun handleResponseGivenResponseWhenResponseCodeIsErrorThenResultFalse() {
        // Inputs
        val opCode = 1
        val responseCode = 1
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 1
        val response = byteArrayOf(0) + opCode.toByte() + 0x0 + 0x0

        // Call
        val packet = MedtrumPacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }
}
