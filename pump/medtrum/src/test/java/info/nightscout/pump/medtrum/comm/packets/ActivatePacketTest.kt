package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.AlarmSetting
import info.nightscout.pump.medtrum.comm.enums.BasalType
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class ActivatePacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActivatePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.tddCalculator = tddCalculator
                it.pumpSync = pumpSync
            }
        }
    }

    @Test fun getRequestGivenPacketWhenValuesSetThenReturnCorrectByteArray() {
        // Inputs
        medtrumPump.desiredPatchExpiration = true
        medtrumPump.desiredAlarmSetting = AlarmSetting.BEEP_ONLY
        medtrumPump.desiredDailyMaxInsulin = 40
        medtrumPump.desiredDailyMaxInsulin = 180

        val basalProfile = byteArrayOf(3, 16, 14, 0, 0, 1, 2, 12, 12, 12)

        // Call
        val packet = ActivatePacket(packetInjector, basalProfile)
        val result = packet.getRequest()

        // Expected values
        val expectedByteArray = byteArrayOf(18, 0, 12, 1, 6, 0, 0, 30, 32, 3, 16, 14, 0, 0, 1, 3, 16, 14, 0, 0, 1, 2, 12, 12, 12)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenPacketWhenValuesSetThenReturnCorrectValues() {
        // Inputs
        medtrumPump.desiredPatchExpiration = true
        medtrumPump.desiredAlarmSetting = AlarmSetting.BEEP_ONLY
        medtrumPump.desiredDailyMaxInsulin = 40
        medtrumPump.desiredDailyMaxInsulin = 180

        val basalProfile = byteArrayOf(3, 16, 14, 0, 0, 1, 2, 12, 12, 12)
        val response = byteArrayOf(26, 18, 19, 1, 0, 0, 41, 0, 0, 0, -104, 91, 28, 17, 1, 30, 0, 1, 0, 41, 0, -104, 91, 28, 17)

        // Call
        val packet = ActivatePacket(packetInjector, basalProfile)
        val result = packet.handleResponse(response)

        // Expected values
        val expectedPatchId = 41L
        val expectedTime = 1675605528000L
        val expectedBasalType = BasalType.STANDARD
        val expectedBasalRate = 1.5
        val expectedBasalSequence = 1
        val expectedBasalPatchId = 41L
        val expectedBasalStart = 1675605528000L

        assertEquals(true, result)
        assertEquals(expectedPatchId, medtrumPump.patchId)
        assertEquals(expectedTime, medtrumPump.lastTimeReceivedFromPump)
        assertEquals(expectedBasalType, medtrumPump.lastBasalType)
        assertEquals(expectedBasalRate, medtrumPump.lastBasalRate, 0.01)
        assertEquals(expectedBasalSequence, medtrumPump.lastBasalSequence)
        assertEquals(expectedBasalPatchId, medtrumPump.lastBasalPatchId)
        assertEquals(expectedBasalStart, medtrumPump.lastBasalStartTime)
        assertEquals(basalProfile, medtrumPump.actualBasalProfile)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(26, 18, 19, 1, 0, 0, 41, 0, 0, 0, -104, 91, 28, 17, 1, 30, 0, 1, 0, 41, 0, -104, 91, 28)

        // Call
        val packet = ActivatePacket(packetInjector, byteArrayOf())
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
    }
}
