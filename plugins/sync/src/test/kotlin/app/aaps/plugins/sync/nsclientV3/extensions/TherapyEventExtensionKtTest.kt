package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class TherapyEventExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toTherapyEvent() {
        var therapyEvent = TherapyEvent(
            timestamp = 10000,
            isValid = true,
            type = TherapyEvent.Type.ANNOUNCEMENT,
            note = "ccccc",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TherapyEvent.MeterType.FINGER,
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.interfaceIdsEqualsTo(therapyEvent2)).isTrue()


        therapyEvent = TherapyEvent(
            timestamp = 10000,
            isValid = true,
            type = TherapyEvent.Type.QUESTION,
            note = null,
            enteredBy = null,
            glucose = null,
            glucoseType = null,
            glucoseUnit = TherapyEvent.GlucoseUnit.MMOL,
            duration = 30000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.interfaceIdsEqualsTo(therapyEvent2)).isTrue()

        therapyEvent = TherapyEvent(
            timestamp = 10000,
            isValid = true,
            type = TherapyEvent.Type.NOTE,
            note = "qqqq",
            enteredBy = null,
            glucose = 10.0,
            glucoseType = null,
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            duration = 0,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.interfaceIdsEqualsTo(therapyEvent2)).isTrue()
    }
}
