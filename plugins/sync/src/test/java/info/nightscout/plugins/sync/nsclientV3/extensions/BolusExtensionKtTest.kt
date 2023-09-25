package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class BolusExtensionKtTest {

    @Test
    fun toBolus() {
        var bolus = Bolus(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            type = Bolus.Type.SMB,
            notes = "aaaa",
            isBasalInsulin = false,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var bolus2 = (bolus.toNSBolus().convertToRemoteAndBack() as NSBolus).toBolus()
        assertThat(bolus.contentEqualsTo(bolus2)).isTrue()
        assertThat(bolus.interfaceIdsEqualsTo(bolus2)).isTrue()

        bolus = Bolus(
            timestamp = 10000,
            isValid = false,
            amount = 1.0,
            type = Bolus.Type.NORMAL,
            notes = "aaaa",
            isBasalInsulin = true,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        bolus2 = (bolus.toNSBolus().convertToRemoteAndBack() as NSBolus).toBolus()
        assertThat(bolus.contentEqualsTo(bolus2)).isTrue()
        assertThat(bolus.interfaceIdsEqualsTo(bolus2)).isTrue()
    }
}
