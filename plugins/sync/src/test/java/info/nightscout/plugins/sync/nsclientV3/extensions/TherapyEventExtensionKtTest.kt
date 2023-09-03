package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSTherapyEvent
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
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
        Assertions.assertTrue(therapyEvent.contentEqualsTo(therapyEvent2))
        Assertions.assertTrue(therapyEvent.interfaceIdsEqualsTo(therapyEvent2))


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
        Assertions.assertTrue(therapyEvent.contentEqualsTo(therapyEvent2))
        Assertions.assertTrue(therapyEvent.interfaceIdsEqualsTo(therapyEvent2))

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
        Assertions.assertTrue(therapyEvent.contentEqualsTo(therapyEvent2))
        Assertions.assertTrue(therapyEvent.interfaceIdsEqualsTo(therapyEvent2))
    }
}