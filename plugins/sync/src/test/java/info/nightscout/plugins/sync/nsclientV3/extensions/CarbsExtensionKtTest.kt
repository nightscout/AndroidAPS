package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
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
        assertThat(carbs.contentEqualsTo(carbs2)).isTrue()
        assertThat(carbs.interfaceIdsEqualsTo(carbs2)).isTrue()

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
        assertThat(carbs.contentEqualsTo(carbs2)).isTrue()
        assertThat(carbs.interfaceIdsEqualsTo(carbs2)).isTrue()
    }
}
