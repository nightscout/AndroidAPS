package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.extension.toByteArray
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class GetTimePacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is GetTimePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.medtrumTimeUtil = medtrumTimeUtil
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 11

        // Call
        val packet = GetTimePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertThat(result).asList().containsExactly(opCode.toByte())
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // Inputs
        val opCode = 11
        val responseCode = 0
        val time: Long = 1234567890
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + time.toByteArray(4)

        // Call       
        val packet = GetTimePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isTrue()
        assertThat(packet.failed).isFalse()
        assertThat(medtrumPump.lastTimeReceivedFromPump).isEqualTo(medtrumTimeUtil.convertPumpTimeToSystemTimeMillis(time))
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 11
        val responseCode = 0
        val time: Long = 1234567890
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + time.toByteArray(4)

        // Call        
        val packet = GetTimePacket(packetInjector)
        val result = packet.handleResponse(response.sliceArray(0..response.size - 2))

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
        assertThat(medtrumPump.lastTimeReceivedFromPump).isEqualTo(0)
    }
}
