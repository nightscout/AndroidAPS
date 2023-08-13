package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class StopPatchPacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

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
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
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
        assertEquals(expectedPatchId, medtrumPump.lastStopPatchId)
        assertEquals(expectedStopSequence, medtrumPump.lastStopSequence)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(11, 31, 10, 0, 0, 0, 23, 0, -110)

        // Call
        val packet = StopPatchPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
        assertTrue(packet.failed)
    }
}
