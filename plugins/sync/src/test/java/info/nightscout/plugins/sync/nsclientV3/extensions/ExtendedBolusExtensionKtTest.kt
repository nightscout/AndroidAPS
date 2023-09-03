package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class ExtendedBolusExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toExtendedBolus() {
        var extendedBolus = ExtendedBolus(
            timestamp = 10000,
            isValid = true,
            amount = 2.0,
            isEmulatingTempBasal = false,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        Assertions.assertTrue(extendedBolus.contentEqualsTo(extendedBolus2))
        Assertions.assertTrue(extendedBolus.interfaceIdsEqualsTo(extendedBolus2))

        extendedBolus = ExtendedBolus(
            timestamp = 10000,
            isValid = true,
            amount = 4.0,
            isEmulatingTempBasal = true,
            duration = 36000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        val converted = extendedBolus.toNSExtendedBolus(validProfile)
        Assertions.assertTrue(converted is NSTemporaryBasal)
        Assertions.assertNotNull((converted as NSTemporaryBasal).extendedEmulated)
        val convertedBack = converted.convertToRemoteAndBack()
        Assertions.assertTrue(convertedBack is NSExtendedBolus)

        extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        Assertions.assertTrue(extendedBolus.contentEqualsTo(extendedBolus2))
        Assertions.assertTrue(extendedBolus.interfaceIdsEqualsTo(extendedBolus2))
    }
}