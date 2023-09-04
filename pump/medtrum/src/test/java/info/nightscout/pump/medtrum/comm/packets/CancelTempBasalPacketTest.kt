package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CancelTempBasalPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

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
        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(opCode.toByte(), result[0])
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val response = byteArrayOf(18, 25, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88, 17)

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        val expectedBasalType = BasalType.STANDARD
        val expectedBasalRate = 1.1
        val expectedBasalSequence = 3
        val expectedStartTime = 1679575392000L
        val expectedPatchId = 146L

        Assertions.assertTrue(result)
        Assertions.assertEquals(expectedBasalType, medtrumPump.lastBasalType)
        Assertions.assertEquals(expectedBasalRate, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(expectedBasalSequence, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(expectedStartTime, medtrumPump.lastBasalStartTime)
        Assertions.assertEquals(expectedPatchId, medtrumPump.lastBasalPatchId)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 25, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertFalse(result)
        Assertions.assertTrue(packet.failed)
    }
}
