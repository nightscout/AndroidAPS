package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class CancelTempBasalPacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is CancelTempBasalPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.dateUtil = dateUtil
                it.medtrumTimeUtil = medtrumTimeUtil
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
        assertThat(result).asList().containsExactly(opCode.toByte())
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

        assertThat(result).isTrue()
        assertThat(medtrumPump.lastBasalType).isEqualTo(expectedBasalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(expectedBasalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(expectedBasalSequence)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(expectedStartTime)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(expectedPatchId)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 25, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)

        // Call
        val packet = CancelTempBasalPacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }
}
