package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
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
        assertThat(temporaryTarget.contentEqualsTo(temporaryTarget2)).isTrue()
        assertThat(temporaryTarget.interfaceIdsEqualsTo(temporaryTarget2)).isTrue()

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
        assertThat(temporaryTarget.contentEqualsTo(temporaryTarget2)).isTrue()
        assertThat(temporaryTarget.interfaceIdsEqualsTo(temporaryTarget2)).isTrue()
    }
}
