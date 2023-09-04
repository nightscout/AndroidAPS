package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SetBasalProfilePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetBasalProfilePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val opCode = 21
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(opCode.toByte()) + 1.toByte() + basalProfile
        Assertions.assertEquals(expected.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val response = byteArrayOf(18, 21, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88, 17)
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
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
        Assertions.assertEquals(basalProfile, medtrumPump.actualBasalProfile)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 21, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
        val result = packet.handleResponse(response)

        // Expected values
        Assertions.assertFalse(result)
        Assertions.assertTrue(packet.failed)
    }
}
