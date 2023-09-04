package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.comm.enums.BasalType
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(medtrumPump.pumpState, MedtrumPumpState.fromByte(state))
    }

    @Test fun handleNotificationGivenBasalDataThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 40, 64, 6, 25, 0, 14, 0, 84, -93, -83, 17, 17, 64, 0, -104, 14, 0, 16)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        Assertions.assertEquals(BasalType.ABSOLUTE_TEMP, medtrumPump.lastBasalType)
        Assertions.assertEquals(0.85, medtrumPump.lastBasalRate, 0.01)
        Assertions.assertEquals(25, medtrumPump.lastBasalSequence)
        Assertions.assertEquals(14, medtrumPump.lastBasalPatchId)
        Assertions.assertEquals(1685126612000, medtrumPump.lastBasalStartTime)
        Assertions.assertEquals(186.80, medtrumPump.reservoir, 0.01)
    }

    @Test fun handleNotificationGivenSequenceAndOtherDataThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 0, 17, -89, 0, 14, 0, 0, 0, 0, 0, 0)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        Assertions.assertEquals(167, medtrumPump.currentSequenceNumber)
    }

    @Test fun handleNotificationGivenBolusInProgressThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 34, 16, 0, 3, 0, -58, 12, 0, 0, 0, 0, 0)
        medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 1)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        Assertions.assertEquals(false, medtrumPump.bolusDone)
        Assertions.assertEquals(0.15, medtrumPump.bolusingTreatment!!.insulin, 0.01)
        Assertions.assertEquals(163.5, medtrumPump.reservoir, 0.01)
    }

    @Test fun handleNotificationGivenBolusFinishedThenDataSaved() {
        // Inputs
        val data = byteArrayOf(32, 34, 17, -128, 33, 0, -89, 12, -80, 0, 14, 0, 0, 0, 0, 0, 0)
        medtrumPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, false, 1)

        // Call
        NotificationPacket(packetInjector).handleNotification(data)

        // Expected values
        Assertions.assertEquals(true, medtrumPump.bolusDone)
        Assertions.assertEquals(1.65, medtrumPump.bolusingTreatment!!.insulin, 0.01)
        Assertions.assertEquals(161.95, medtrumPump.reservoir, 0.01)
    }
}
