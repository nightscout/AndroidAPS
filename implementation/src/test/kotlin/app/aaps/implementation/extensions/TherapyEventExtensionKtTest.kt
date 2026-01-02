package app.aaps.implementation.extensions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.implementation.pump.isOlderThan
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class TherapyEventExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun isOlderThan() {
        val therapyEvent = TE(
            timestamp = now,
            isValid = true,
            type = TE.Type.ANNOUNCEMENT,
            note = "c",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "b"
            )
        )
        whenever(dateUtil.now()).thenReturn(now + T.mins(30).msecs())
        assertThat(therapyEvent.isOlderThan(1, dateUtil)).isFalse()
        whenever(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        assertThat(therapyEvent.isOlderThan(1, dateUtil)).isTrue()
    }
}
