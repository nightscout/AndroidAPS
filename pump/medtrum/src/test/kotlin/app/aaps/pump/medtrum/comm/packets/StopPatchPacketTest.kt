package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test

class StopPatchPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is StopPatchPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 31

        // Call
        val packet = StopPatchPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertThat(result).asList().containsExactly(opCode.toByte())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val response = byteArrayOf(11, 31, 10, 0, 0, 0, 23, 0, -110, 0, -5, 0)

        // Call
        val packet = StopPatchPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        val expectedPatchId = 146L
        val expectedStopSequence = 23
        assertThat(result).isTrue()
        assertThat(medtrumPump.lastStopPatchId).isEqualTo(expectedPatchId)
        assertThat(medtrumPump.lastStopSequence).isEqualTo(expectedStopSequence)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(11, 31, 10, 0, 0, 0, 23, 0, -110)

        // Call
        val packet = StopPatchPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }
}
