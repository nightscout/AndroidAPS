package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class TemporaryBasalExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toTemporaryBasal() {
        var temporaryBasal = TB(
            timestamp = 10000,
            isValid = true,
            type = TB.Type.NORMAL,
            rate = 2.0,
            isAbsolute = true,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var temporaryBasal2 = (temporaryBasal.toNSTemporaryBasal(validProfile).convertToRemoteAndBack() as NSTemporaryBasal).toTemporaryBasal()
        assertThat(temporaryBasal.contentEqualsTo(temporaryBasal2)).isTrue()
        assertThat(temporaryBasal.ids.contentEqualsTo(temporaryBasal2.ids)).isTrue()

        temporaryBasal = TB(
            timestamp = 10000,
            isValid = true,
            type = TB.Type.PUMP_SUSPEND,
            rate = 120.0,
            isAbsolute = false,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        temporaryBasal2 = (temporaryBasal.toNSTemporaryBasal(validProfile).convertToRemoteAndBack() as NSTemporaryBasal).toTemporaryBasal()
        assertThat(temporaryBasal.contentEqualsTo(temporaryBasal2)).isTrue()
        assertThat(temporaryBasal.ids.contentEqualsTo(temporaryBasal2.ids)).isTrue()
    }
}
