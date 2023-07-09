package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class CancelTempBasalPacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is CancelTempBasalPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.dateUtil = dateUtil
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 25

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        assertEquals(1, result.size)
        assertEquals(opCode.toByte(), result[0])
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val repsonse = byteArrayOf(18, 25, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88, 17)

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.handleResponse(repsonse)

        // Expected values
        val expectedBasalType = BasalType.STANDARD
        val expectedBasalRate = 1.1
        val expectedBasalSequence = 3
        val expectedStartTime = 1679575392000L
        val expectedPatchId = 146L

        assertTrue(result)
        assertEquals(expectedBasalType, medtrumPump.lastBasalType)
        assertEquals(expectedBasalRate, medtrumPump.lastBasalRate, 0.01)
        assertEquals(expectedBasalSequence, medtrumPump.lastBasalSequence)
        assertEquals(expectedStartTime, medtrumPump.lastBasalStartTime)
        assertEquals(expectedPatchId, medtrumPump.lastBasalPatchId)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 25, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
        assertTrue(packet.failed)
    }
}
