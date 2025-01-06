package app.aaps.pump.medtrum.comm.packets

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import app.aaps.pump.medtrum.MedtrumTestBase
import app.aaps.pump.medtrum.comm.enums.AlarmSetting
import org.junit.jupiter.api.Test

class SetPatchPacketTest : MedtrumTestBase() {

    /** Test packet specific behavior */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is SetPatchPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenValuesWhenCalledThenReturnValidArray() {
        // Inputs
        medtrumPump.desiredPatchExpiration = false
        medtrumPump.desiredAlarmSetting = AlarmSetting.LIGHT_AND_VIBRATE
        medtrumPump.desiredDailyMaxInsulin = 40
        medtrumPump.desiredDailyMaxInsulin = 180

        // Call
        val packet = SetPatchPacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val expected = byteArrayOf(35, 1, 32, 3, 16, 14, 0, 0, 12, 0, 0, 30)
        assertThat(result).asList().containsExactlyElementsIn(expected.toList()).inOrder()
    }
}
