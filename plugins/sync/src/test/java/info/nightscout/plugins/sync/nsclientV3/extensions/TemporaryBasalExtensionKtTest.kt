package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class TemporaryBasalExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toTemporaryBasal() {
        var temporaryBasal = TemporaryBasal(
            timestamp = 10000,
            isValid = true,
            type = TemporaryBasal.Type.NORMAL,
            rate = 2.0,
            isAbsolute = true,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var temporaryBasal2 = (temporaryBasal.toNSTemporaryBasal(validProfile).convertToRemoteAndBack() as NSTemporaryBasal).toTemporaryBasal()
        Assertions.assertTrue(temporaryBasal.contentEqualsTo(temporaryBasal2))
        Assertions.assertTrue(temporaryBasal.interfaceIdsEqualsTo(temporaryBasal2))

        temporaryBasal = TemporaryBasal(
            timestamp = 10000,
            isValid = true,
            type = TemporaryBasal.Type.PUMP_SUSPEND,
            rate = 120.0,
            isAbsolute = false,
            duration = 30000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        temporaryBasal2 = (temporaryBasal.toNSTemporaryBasal(validProfile).convertToRemoteAndBack() as NSTemporaryBasal).toTemporaryBasal()
        Assertions.assertTrue(temporaryBasal.contentEqualsTo(temporaryBasal2))
        Assertions.assertTrue(temporaryBasal.interfaceIdsEqualsTo(temporaryBasal2))
    }
}