package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class GetTimePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is GetTimePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
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
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
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
        assertEquals(true, result)
        assertEquals(false, packet.failed)
        assertEquals(MedtrumTimeUtil().convertPumpTimeToSystemTimeMillis(time), medtrumPump.lastTimeReceivedFromPump)
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
        assertEquals(false, result)
        assertEquals(true, packet.failed)
        assertEquals(0, medtrumPump.lastTimeReceivedFromPump)
    }
}
