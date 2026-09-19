package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers the xdrip [GV] toXdripJson: device/mills/mgdl/direction mapping. */
class GlucoseValueExtensionTest : TestBase() {

    @Test
    fun toXdripJson_mapsFields() {
        val gv = GV(
            id = 1L, timestamp = 1000L, value = 120.0, isValid = true, utcOffset = 0,
            raw = 120000.0, trendArrow = TrendArrow.FLAT, noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G6_NATIVE, ids = IDs()
        )
        val json = gv.toXdripJson()
        assertThat(json.getString("device")).isEqualTo(SourceSensor.DEXCOM_G6_NATIVE.text)
        assertThat(json.getLong("mills")).isEqualTo(1000L)
        assertThat(json.getBoolean("isValid")).isTrue()
        assertThat(json.getDouble("mgdl")).isEqualTo(120.0)
        assertThat(json.getString("direction")).isEqualTo(TrendArrow.FLAT.text)
    }
}
