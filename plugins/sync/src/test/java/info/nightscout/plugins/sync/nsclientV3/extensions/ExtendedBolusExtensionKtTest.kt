package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Test

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
        assertThat(converted).isInstanceOf(NSTemporaryBasal::class.java)
        assertThat((converted as NSTemporaryBasal).extendedEmulated).isNotNull()
        val convertedBack = converted.convertToRemoteAndBack()
        assertThat(convertedBack).isInstanceOf(NSExtendedBolus::class.java)

        extendedBolus2 = (extendedBolus.toNSExtendedBolus(validProfile).convertToRemoteAndBack() as NSExtendedBolus).toExtendedBolus()
        assertThat(extendedBolus.contentEqualsTo(extendedBolus2)).isTrue()
        assertThat(extendedBolus.interfaceIdsEqualsTo(extendedBolus2)).isTrue()
    }
}
