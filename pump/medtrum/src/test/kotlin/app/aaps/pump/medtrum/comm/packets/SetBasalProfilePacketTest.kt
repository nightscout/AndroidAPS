package app.aaps.pump.medtrum.comm.packets

import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.BasalType
import app.aaps.pump.medtrum.util.MedtrumTimeUtil
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class SetBasalProfilePacketTest : MedtrumTestBase() {

    val medtrumTimeUtil = MedtrumTimeUtil()

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetBasalProfilePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
                it.medtrumTimeUtil = medtrumTimeUtil
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
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
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

        assertThat(result).isTrue()
        assertThat(medtrumPump.lastBasalType).isEqualTo(expectedBasalType)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(expectedBasalRate)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(expectedBasalSequence)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(expectedStartTime)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(expectedPatchId)
        assertThat(medtrumPump.actualBasalProfile).isEqualTo(basalProfile)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val response = byteArrayOf(18, 21, 16, 0, 0, 0, 1, 22, 0, 3, 0, -110, 0, -32, -18, 88)
        val basalProfile = byteArrayOf(8, 2, 3, 4, -1, 0, 0, 0, 0)

        // Call
        val packet = SetBasalProfilePacket(packetInjector, basalProfile)
        val result = packet.handleResponse(response)

        // Expected values
        assertThat(result).isFalse()
        assertThat(packet.failed).isTrue()
    }
}
