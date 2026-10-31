package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.CA
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class CarbsExtensionKtTest {

    @Test
    fun toCarbs() {
        var carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            duration = 0,
            notes = "aaaa",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var carbs2 = (carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs()
        assertThat(carbs.contentEqualsTo(carbs2)).isTrue()
        assertThat(carbs.ids.contentEqualsTo(carbs2.ids)).isTrue()

        carbs = CA(
            timestamp = 10000,
            isValid = false,
            amount = 1.0,
            duration = 60000,
            notes = null,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        carbs2 = (carbs.toNSCarbs().convertToRemoteAndBack() as NSCarbs).toCarbs()
        assertThat(carbs.contentEqualsTo(carbs2)).isTrue()
        assertThat(carbs.ids.contentEqualsTo(carbs2.ids)).isTrue()
    }
}
