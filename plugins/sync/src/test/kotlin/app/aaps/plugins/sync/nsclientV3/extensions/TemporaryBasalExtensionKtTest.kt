package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
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
        assertThat(temporaryBasal.contentEqualsTo(temporaryBasal2)).isTrue()
        assertThat(temporaryBasal.interfaceIdsEqualsTo(temporaryBasal2)).isTrue()

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
        assertThat(temporaryBasal.contentEqualsTo(temporaryBasal2)).isTrue()
        assertThat(temporaryBasal.interfaceIdsEqualsTo(temporaryBasal2)).isTrue()
    }
}
