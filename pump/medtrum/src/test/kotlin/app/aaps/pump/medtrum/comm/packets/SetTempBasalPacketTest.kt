package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class SetTempBasalPacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetTempBasalPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.medtrumTimeUtil = medtrumTimeUtil
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
        assertThat(result.contentToString()).isEqualTo(expected.contentToString())
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

        assertThat(result).isTrue()
        assertThat(medtrumPump.lastBasalType).isEqualTo(expectedBasalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(expectedBasalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(expectedBasalSequence)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(expectedStartTime)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(expectedPatchId)
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
        assertThat(result).isFalse()
    }
}
