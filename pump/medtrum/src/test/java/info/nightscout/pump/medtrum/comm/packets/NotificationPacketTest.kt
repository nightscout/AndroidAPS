package info.nightscout.pump.medtrum.comm.packets

import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import org.junit.jupiter.api.Test

class NotificationPacketTest : MedtrumTestBase() {

    /** Test base behavior of the Notification packet  */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is NotificationPacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun handleNotificationGivenStatusAndDataThenStateSaved() {
        // Inputs
        val state: Byte = 1

        // Call
        NotificationPacket(packetInjector).handleNotification(byteArrayOf(state))

        // Expected values
        assertThat(MedtrumPumpState.fromByte(state)).isEqualTo(medtrumPump.pumpState)
    }

    @Test fun handleNotificationGivenBasalDataThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 40, 64, 6, 25, 0, 14, 0, 84, -93, -83, 17, 17, 64, 0, -104, 14, 0, 16)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        assertThat(medtrumPump.lastBasalType).isEqualTo(BasalType.ABSOLUTE_TEMP)
        assertThat(medtrumPump.lastBasalRate).isWithin(0.01).of(0.85)
        assertThat(medtrumPump.lastBasalSequence).isEqualTo(25)
        assertThat(medtrumPump.lastBasalPatchId).isEqualTo(14)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(1685126612000)
        assertThat(medtrumPump.reservoir).isWithin(0.01).of(186.80)
    }

    @Test fun handleNotificationGivenSequenceAndOtherDataThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 0, 17, -89, 0, 14, 0, 0, 0, 0, 0, 0)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(167)
    }

    @Test fun handleNotificationGivenBolusInProgressThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 34, 16, 0, 3, 0, -58, 12, 0, 0, 0, 0, 0)
        medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 1)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        assertThat(medtrumPump.bolusDone).isFalse()
        assertThat(medtrumPump.bolusingTreatment!!.insulin).isWithin(0.01).of(0.15)
        assertThat(medtrumPump.reservoir).isWithin(0.01).of(163.5)
    }

    @Test fun handleNotificationGivenBolusFinishedThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 34, 17, -128, 33, 0, -89, 12, -80, 0, 14, 0, 0, 0, 0, 0, 0)
        medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 1)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        assertThat(medtrumPump.bolusDone).isTrue()
        assertThat(medtrumPump.bolusingTreatment!!.insulin).isWithin(0.01).of(1.65)
        assertThat(medtrumPump.reservoir).isWithin(0.01).of(161.95)
    }

    @Test fun handleNotificationGivenFieldMaskButMessageTooShortThenNothingSaved() {
        // Inputs
        val data = byteArrayOf(67, 41, 67, -1, 122, 95, 18, 0, 73, 1, 19, 0, 1, 0, 20, 0, 0, 0, 0, 16)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        assertThat(medtrumPump.suspendTime).isEqualTo(0)
        assertThat(medtrumPump.lastBasalStartTime).isEqualTo(0)
        assertThat(medtrumPump.currentSequenceNumber).isEqualTo(0)
    }
}
