package info.nightscout.core.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.isOlderThan
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TherapyEventExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun isOlderThan() {
        val therapyEvent = TherapyEvent(
            timestamp = now,
            isValid = true,
            type = TherapyEvent.Type.ANNOUNCEMENT,
            note = "c",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TherapyEvent.MeterType.FINGER,
            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "b"
            )
        )
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.mins(30).msecs())
        assertThat(therapyEvent.isOlderThan(1.0, dateUtil)).isFalse()
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        assertThat(therapyEvent.isOlderThan(1.0, dateUtil)).isTrue()
    }
}
