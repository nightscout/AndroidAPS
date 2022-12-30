package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions

import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class BolusExtensionKtTest {

    @Test
    fun toBolus() {
        var bolus = Bolus(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            type = Bolus.Type.SMB,
            notes = "aaaa",
            isBasalInsulin = false,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var bolus2 = (bolus.toNSBolus().convertToRemoteAndBack() as NSBolus).toBolus()
        Assertions.assertTrue(bolus.contentEqualsTo(bolus2))
        Assertions.assertTrue(bolus.interfaceIdsEqualsTo(bolus2))

        bolus = Bolus(
            timestamp = 10000,
            isValid = false,
            amount = 1.0,
            type = Bolus.Type.NORMAL,
            notes = "aaaa",
            isBasalInsulin = true,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        bolus2 = (bolus.toNSBolus().convertToRemoteAndBack() as NSBolus).toBolus()
        Assertions.assertTrue(bolus.contentEqualsTo(bolus2))
        Assertions.assertTrue(bolus.interfaceIdsEqualsTo(bolus2))
    }
}