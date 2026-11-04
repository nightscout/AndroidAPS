package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class ExtendedBolusExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toExtendedBolus() {
        var extendedBolus = EB(
            timestamp = 10000,
            isValid = true,
            amount = 2.0,
            isEmulatingTempBasal = false,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        assertThat(extendedBolus.contentEqualsTo(extendedBolus2)).isTrue()
        assertThat(extendedBolus.ids.contentEqualsTo(extendedBolus2.ids)).isTrue()

        extendedBolus = EB(
            timestamp = 10000,
            isValid = true,
            amount = 4.0,
            isEmulatingTempBasal = true,
            duration = 36000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        val converted = extendedBolus.toNSExtendedBolus(validProfile)
        assertIs<NSTemporaryBasal>(converted)
        assertThat(converted.extendedEmulated).isNotNull()
        val convertedBack = converted.convertToRemoteAndBack()
        assertIs<NSExtendedBolus>(convertedBack)

        extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        assertThat(extendedBolus.contentEqualsTo(extendedBolus2)).isTrue()
        assertThat(extendedBolus.ids.contentEqualsTo((extendedBolus2.ids))).isTrue()
    }
}
