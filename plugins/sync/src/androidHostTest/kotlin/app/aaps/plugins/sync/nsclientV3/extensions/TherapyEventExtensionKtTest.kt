package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class TherapyEventExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toTherapyEvent() {
        var therapyEvent = TE(
            timestamp = 10000,
            isValid = true,
            type = TE.Type.ANNOUNCEMENT,
            note = "ccccc",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.ids.contentEqualsTo(therapyEvent2.ids)).isTrue()


        therapyEvent = TE(
            timestamp = 10000,
            isValid = true,
            type = TE.Type.QUESTION,
            note = null,
            enteredBy = null,
            glucose = null,
            glucoseType = null,
            glucoseUnit = GlucoseUnit.MMOL,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.ids.contentEqualsTo(therapyEvent2.ids)).isTrue()

        therapyEvent = TE(
            timestamp = 10000,
            isValid = true,
            type = TE.Type.NOTE,
            note = "qqqq",
            enteredBy = null,
            glucose = 10.0,
            glucoseType = null,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 0,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        therapyEvent2 = (therapyEvent.toNSTherapyEvent().convertToRemoteAndBack() as NSTherapyEvent).toTherapyEvent()
        assertThat(therapyEvent.contentEqualsTo(therapyEvent2)).isTrue()
        assertThat(therapyEvent.ids.contentEqualsTo(therapyEvent2.ids)).isTrue()
    }
}
