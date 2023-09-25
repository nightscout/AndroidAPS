package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        val glucoseValue2 = glucoseValue.toNSSvgV3().convertToRemoteAndBack()?.toTransactionGlucoseValue()?.toGlucoseValue()
        assertThat(glucoseValue.contentEqualsTo(glucoseValue2!!)).isTrue()
        assertThat(glucoseValue.interfaceIdsEqualsTo(glucoseValue2)).isTrue()
    }
}
