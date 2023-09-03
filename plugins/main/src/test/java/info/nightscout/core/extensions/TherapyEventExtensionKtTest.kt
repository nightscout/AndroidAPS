package info.nightscout.core.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
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
        Assertions.assertFalse(therapyEvent.isOlderThan(1.0, dateUtil))
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        Assertions.assertTrue(therapyEvent.isOlderThan(1.0, dateUtil))
    }
}