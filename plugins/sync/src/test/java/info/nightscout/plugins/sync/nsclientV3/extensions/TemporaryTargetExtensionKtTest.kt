package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class TemporaryTargetExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toTemporaryTarget() {
        var temporaryTarget = TemporaryTarget(
            timestamp = 10000,
            isValid = true,
            reason = TemporaryTarget.Reason.ACTIVITY,
            highTarget = 100.0,
            lowTarget = 99.0,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var temporaryTarget2 = (temporaryTarget.toNSTemporaryTarget().convertToRemoteAndBack() as NSTemporaryTarget).toTemporaryTarget()
        Assertions.assertTrue(temporaryTarget.contentEqualsTo(temporaryTarget2))
        Assertions.assertTrue(temporaryTarget.interfaceIdsEqualsTo(temporaryTarget2))

        temporaryTarget = TemporaryTarget(
            timestamp = 10000,
            isValid = true,
            reason = TemporaryTarget.Reason.CUSTOM,
            highTarget = 150.0,
            lowTarget = 150.0,
            duration = 30000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        temporaryTarget2 = (temporaryTarget.toNSTemporaryTarget().convertToRemoteAndBack() as NSTemporaryTarget).toTemporaryTarget()
        Assertions.assertTrue(temporaryTarget.contentEqualsTo(temporaryTarget2))
        Assertions.assertTrue(temporaryTarget.interfaceIdsEqualsTo(temporaryTarget2))
    }
}