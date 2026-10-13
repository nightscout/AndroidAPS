package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import app.aaps.plugins.sync.nsShared.extensions.contentEqualsTo
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class GlucoseValueExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun toGlucoseValue() {
        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
            ids = IDs(
                nightscoutId = "nightscoutId"
            )
        )

        val glucoseValue2 = glucoseValue.toNSSvgV3().convertToRemoteAndBack()?.toGV()
        assertThat(glucoseValue.contentEqualsTo(glucoseValue2!!)).isTrue()
        assertThat(glucoseValue.ids.contentEqualsTo(glucoseValue2.ids)).isTrue()
    }
}
