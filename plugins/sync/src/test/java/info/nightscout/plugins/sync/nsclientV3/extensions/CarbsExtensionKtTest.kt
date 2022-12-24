package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class CarbsExtensionKtTest {

    @Test
    fun toCarbs() {
        var carbs = Carbs(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            duration = 0,
            notes = "aaaa",
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var carbs2 = (carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs()
        Assertions.assertTrue(carbs.contentEqualsTo(carbs2))
        Assertions.assertTrue(carbs.interfaceIdsEqualsTo(carbs2))

        carbs = Carbs(
            timestamp = 10000,
            isValid = false,
            amount = 1.0,
            duration = 60000,
            notes = null,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        carbs2 = (carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs()
        Assertions.assertTrue(carbs.contentEqualsTo(carbs2))
        Assertions.assertTrue(carbs.interfaceIdsEqualsTo(carbs2))
    }
}