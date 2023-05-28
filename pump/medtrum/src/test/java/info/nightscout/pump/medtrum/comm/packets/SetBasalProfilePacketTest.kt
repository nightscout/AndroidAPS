package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetBasalProfilePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

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
        assertEquals(expected.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val repsonse = byteArrayOf(18, 21, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88, 17)
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
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
        assertEquals(basalProfile, medtrumPump.actualBasalProfile)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 21, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
        assertTrue(packet.failed)
    }
}
