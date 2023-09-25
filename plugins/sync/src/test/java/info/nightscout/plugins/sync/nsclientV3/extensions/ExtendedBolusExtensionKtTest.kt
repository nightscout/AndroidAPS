package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

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
        assertThat(extendedBolus.contentEqualsTo(extendedBolus2)).isTrue()
        assertThat(extendedBolus.interfaceIdsEqualsTo(extendedBolus2)).isTrue()

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
        assertIs<NSTemporaryBasal>(converted)
        assertThat(converted.extendedEmulated).isNotNull()
        val convertedBack = converted.convertToRemoteAndBack()
        assertIs<NSExtendedBolus>(convertedBack)

        extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        assertThat(extendedBolus.contentEqualsTo(extendedBolus2)).isTrue()
        assertThat(extendedBolus.interfaceIdsEqualsTo(extendedBolus2)).isTrue()
    }
}
