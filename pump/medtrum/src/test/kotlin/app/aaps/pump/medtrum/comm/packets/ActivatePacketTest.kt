package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.AlarmSetting
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class ActivatePacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActivatePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.tddCalculator = tddCalculator
                it.pumpSync = pumpSync
                it.medtrumTimeUtil = medtrumTimeUtil
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
        assertThat(result.contentToString()).isEqualTo(expectedByteArray.contentToString())
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

        assertThat(result).isTrue()
        assertThat(medtrumPump.patchId).isEqualTo(expectedPatchId)
        assertThat(medtrumPump.lastTimeReceivedFromPump).isEqualTo(expectedTime)
        assertThat(medtrumPump.lastBasalType).isEqualTo(expectedBasalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(expectedBasalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(expectedBasalSequence)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(expectedBasalPatchId)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(expectedBasalStart)
        assertThat(medtrumPump.actualBasalProfile).isEqualTo(basalProfile)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(26, 18, 19, 1, 0, 0, 41, 0, 0, 0, -104, 91, 28, 17, 1, 30, 0, 1, 0, 41, 0, -104, 91, 28)

        // Call
        val packet = ActivatePacket(packetInjector, byteArrayOf())
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
    }
}
