package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.sdk.mapper.convertToRemoteAndBack
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class GlucoseValueExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toGlucoseValue() {
        val glucoseValue = GlucoseValue(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = GlucoseValue.TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId"
            )
        )

        var glucoseValue2 = glucoseValue.toNSSvgV3().convertToRemoteAndBack()?.toTransactionGlucoseValue()?.toGlucoseValue()
        Assertions.assertTrue(glucoseValue.contentEqualsTo(glucoseValue2!!))
        Assertions.assertTrue(glucoseValue.interfaceIdsEqualsTo(glucoseValue2))
    }
}