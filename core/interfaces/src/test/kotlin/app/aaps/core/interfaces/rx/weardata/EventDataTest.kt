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
        EventData.ActionTempTargetConfirmed(false).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionBolusConfirmed(1.0, 2).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionECarbsConfirmed(1, 2, 3).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionFillConfirmed(1.0).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
        EventData.ActionProfileSwitchConfirmed(1, 2, 3).let {
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
        EventData.SnoozeAlert(1).let {
            assertThat(EventData.deserializeByte(it.serializeByte())).isEqualTo(it)
            assertThat(EventData.deserialize(it.serialize())).isEqualTo(it)
        }
    }
}
