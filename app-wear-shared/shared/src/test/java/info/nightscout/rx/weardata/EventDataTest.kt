package info.nightscout.rx.weardata

import info.nightscout.androidaps.TestBase
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Assertions

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
class EventDataTest : TestBase() {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun serializationTest() {
        EventData.ActionPong(1, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.WearException(1, byteArrayOf(0xAA.toByte()), "board", "fingerprint", "sdk", "model", "manufacturer", "product").let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.Error(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.CancelBolus(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionResendData("data").let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionPumpStatus(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionLoopStatus(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionTddStatus(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionECarbsPreCheck(1, 2, 3).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionBolusPreCheck(1.0, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionFillPreCheck(1.0).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionFillPresetPreCheck(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionProfileSwitchSendInitialData(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionProfileSwitchPreCheck(1, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionWizardPreCheck(1, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionQuickWizardPreCheck("guid").let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionHeartRate(1, 2, 3.0, "device").let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionTempTargetPreCheck(EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionWizardConfirmed(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionTempTargetConfirmed(false).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionBolusConfirmed(1.0, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionECarbsConfirmed(1, 2, 3).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionFillConfirmed(1.0).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionProfileSwitchConfirmed(1, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.OpenLoopRequestConfirmed(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.CancelNotification(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        // EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(CwfData())).let {
        //     Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
        //     Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        // }
        EventData.ActionPing(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.OpenSettings(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.BolusProgress(1, "status").let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.SingleBg(1, sgv = 2.0, high = 3.0, low = 4.0).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.GraphData(arrayListOf(EventData.SingleBg(1, sgv = 2.0, high = 3.0, low = 4.0))).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.TreatmentData(
            arrayListOf(EventData.TreatmentData.TempBasal(1, 2.0, 3, 4.0, 5.0)),
            arrayListOf(EventData.TreatmentData.Basal(1, 2, 3.0)),
            arrayListOf(EventData.TreatmentData.Treatment(1, 2.0, 3.0, true, true)),
            arrayListOf(EventData.SingleBg(1, sgv = 2.0, high = 3.0, low = 4.0))
        ).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.Preferences(1, true, true, 2, 3, 4.0, 5.0, 6.0, 7, 8).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.QuickWizard(arrayListOf(EventData.QuickWizard.QuickWizardEntry("1", "2", 3, 4, 5))).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        // EventData.ActionSetCustomWatchface().let {
        //     Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
        //     Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        // }
        EventData.ActionrequestCustomWatchface(true).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionrequestSetDefaultWatchface(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ActionProfileSwitchOpenActivity(1, 2).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.OpenLoopRequest("1", "2", null).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.ConfirmAction("1", "2", null).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
        EventData.SnoozeAlert(1).let {
            Assertions.assertEquals(it, EventData.deserializeByte(it.serializeByte()))
            Assertions.assertEquals(it, EventData.deserialize(it.serialize()))
        }
    }
}