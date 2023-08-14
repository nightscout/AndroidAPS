package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class SetTempBasalPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetTempBasalPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketWhenCalledThenReturnOpCode() {
        // Inputs
        val absoluteRate = 1.25
        val duration = 60

        // Call
        val packet = SetTempBasalPacket(packetInjector, absoluteRate, duration)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(24, 6, 25, 0, 60, 0)
        assertEquals(expected.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        val absoluteRate = 1.25
        val duration = 60

        val response = byteArrayOf(18, 24, 12, 0, 0, 0, 6, 25, 0, 2, 0, -110, 0, -56, -19, 88, 17, -89, 0)

        // Call
        val packet = SetTempBasalPacket(packetInjector, absoluteRate, duration)
        val result = packet.handleResponse(response)

        // Expected values
        val expectedBasalType = BasalType.ABSOLUTE_TEMP
        val expectedBasalRate = 1.25
        val expectedBasalSequence = 2
        val expectedStartTime = 1679575112000L
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
        val absoluteRate = 1.25
        val duration = 60

        val response = byteArrayOf(18, 24, 12, 0, 0, 0, 6, 25, 0, 2, 0, -110, 0, -56, -19, 88)

        // Call
        val packet = SetTempBasalPacket(packetInjector, absoluteRate, duration)
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
    }
}
