package app.aaps.core.interfaces.rx.weardata

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExperimentalSerializationApi
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventDataTest {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun serializationTest() {
        EventData.ActionPong(1, 2).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.WearException(1, byteArrayOf(0xAA.toByte()), "board", "fingerprint", "sdk", "model", "manufacturer", "product").let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.Error(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.CancelBolus(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionResendData("data").let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionPumpStatus(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionLoopStatus(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionTddStatus(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionECarbsPreCheck(1, 2, 3).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionBolusPreCheck(1.0, 2).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionFillPreCheck(1.0).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionFillPresetPreCheck(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionProfileSwitchSendInitialData(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionProfileSwitchPreCheck(1, 2, 3).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionWizardPreCheck(1, 2).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionQuickWizardPreCheck("guid").let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionHeartRate(1, 2, 3.0, "device").let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionWizardConfirmed(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionTempTargetConfirmed(1L).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionBolusConfirmed(1L).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionECarbsConfirmed(2L).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionFillConfirmed(1.0).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionProfileSwitchConfirmed(99L).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.OpenLoopRequestConfirmed(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.CancelNotification(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        // EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(CwfData())).let {
        //     assertThat( EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
        //     assertThat( EventData.deserialize(it.serialize())).isEqualTo(it)
        // }
        EventData.ActionPing(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.OpenSettings(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.BolusProgress(1, "status").let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.SingleBg(dataset = 0, 1, sgv = 2.0, high = 3.0, low = 4.0).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.GraphData(arrayListOf(EventData.SingleBg(dataset = 0, 1, sgv = 2.0, high = 3.0, low = 4.0))).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.TreatmentData(
            arrayListOf(EventData.TreatmentData.TempBasal(1, 2.0, 3, 4.0, 5.0)),
            arrayListOf(EventData.TreatmentData.Basal(1, 2, 3.0)),
            arrayListOf(EventData.TreatmentData.Treatment(1, 2.0, 3.0, true, isValid = true)),
            arrayListOf(EventData.SingleBg(dataset = 0, 1, sgv = 2.0, high = 3.0, low = 4.0))
        ).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.Preferences(1, wearControl = true, true, 2, 3, 4.0, 5.0, 6.0, 7, 8).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.Status(
            dataset = 0, externalStatus = "st", iobSum = "1", iobDetail = "2", cob = "3", currentBasal = "4",
            battery = "5", rigBattery = "6", openApsStatus = 7L, bgi = "8", batteryLevel = 9, patientName = "p",
            tempTarget = "t", tempTargetLevel = 1, tempTargetDuration = 10L, reservoirString = "r",
            reservoir = 11.0, reservoirLevel = 0, cobValue = 12.0, loopMode = LoopStatusData.LoopMode.SUSPENDED,
            modeEndTime = 13L
        ).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.QuickWizard(arrayListOf(EventData.QuickWizard.QuickWizardEntry("1", "2", 3, 4, 5))).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        // EventData.ActionSetCustomWatchface().let {
        //     assertThat( EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
        //     assertThat( EventData.deserialize(it.serialize())).isEqualTo(it)
        // }
        EventData.ActionrequestCustomWatchface(true).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionrequestSetDefaultWatchface(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionProfileSwitchOpenActivity(1, 2, 3).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.OpenLoopRequest("1", "2", null).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ConfirmAction("1", "2", null).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ConfirmAction(
            "1", "2", null,
            lines = listOf(EventData.ConfirmActionLine("BOLUS", "Bolus: 1.5 U"), EventData.ConfirmActionLine("CARBS", "Carbs: 30 g"))
        ).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.SnoozeAlert(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        // Running mode now rides the generic confirm path: Selected/Confirmed + the master-authored lines.
        EventData.RunningModeSelected(1, 2, 60).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.RunningModeConfirmed(1234567890L).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ConfirmAction(
            "Running mode", "", EventData.RunningModeConfirmed(42L),
            lines = listOf(EventData.ConfirmActionLine("PRIMARY", "Running mode: Closed Loop"), EventData.ConfirmActionLine("NORMAL", "Duration: 60 min"))
        ).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        // Watch-on-client insulin relay feedback: the spinner trigger, the commit-success terminal, and a deferred confirm.
        EventData.ContactingMaster.let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.RemoteDelivered.let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ConfirmAction(
            "Bolus", "", EventData.ActionBolusConfirmed(7L),
            lines = listOf(EventData.ConfirmActionLine("BOLUS", "Bolus: 1.5 U")), deferConfirm = true
        ).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
    }

    @Test
    fun deserializeToleratesUnknownKeysFromNewerPeer() {
        // A newer peer may add fields this build doesn't know (phone and wear are not always
        // updated together) — decoding must not fall back to Error, or screens waiting for the
        // event spin forever
        val event = EventData.LoopStatusResponse(
            timeStamp = 1L,
            data = LoopStatusData(0L, LoopStatusData.LoopMode.DISCONNECTED, null, null, null, null, null, TargetRange("a", "b", "c", "u"), null)
        )
        val topLevelUnknown = event.serialize().replaceFirst("\"timeStamp\"", "\"futureField\":42,\"timeStamp\"")
        assertThat(EventData.deserialize(topLevelUnknown)).isEqualTo(event)

        val nestedUnknown = event.serialize().replaceFirst("\"loopMode\"", "\"futureField\":42,\"loopMode\"")
        assertThat(EventData.deserialize(nestedUnknown)).isEqualTo(event)
    }

    @Test
    fun statusFromOlderPeerDefaultsLoopModeToUnknown() {
        // A phone older than the loopMode field sends Status without it — the watch must default
        // to UNKNOWN instead of failing to decode
        val status = EventData.Status(
            dataset = 0, externalStatus = "st", iobSum = "1", iobDetail = "2", cob = "3", currentBasal = "4",
            battery = "5", rigBattery = "6", openApsStatus = 7L, bgi = "8", batteryLevel = 9, patientName = "p",
            tempTarget = "t", tempTargetLevel = 1, tempTargetDuration = 10L, reservoirString = "r",
            reservoir = 11.0, reservoirLevel = 0, cobValue = 12.0, loopMode = LoopStatusData.LoopMode.CLOSED
        )
        val legacyJson = status.serialize().replaceFirst(",\"loopMode\":\"CLOSED\"", "")
        assertThat(legacyJson).doesNotContain("loopMode")
        val restored = EventData.deserialize(legacyJson) as EventData.Status
        assertThat(restored.loopMode).isEqualTo(LoopStatusData.LoopMode.UNKNOWN)
    }
}
