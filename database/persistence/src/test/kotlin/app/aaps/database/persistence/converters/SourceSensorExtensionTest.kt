package app.aaps.database.persistence.converters

import app.aaps.core.data.model.SourceSensor
import app.aaps.database.entities.GlucoseValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class SourceSensorExtensionTest {

    @Test
    fun `domain toDb then fromDb is stable for all values`() {
        SourceSensor.entries.forEach { value ->
            assertThat(value.toDb().fromDb()).isEqualTo(value)
        }
    }

    @Test
    fun `entity fromDb then toDb is stable for all values`() {
        GlucoseValue.SourceSensor.entries.forEach { value ->
            assertThat(value.fromDb().toDb()).isEqualTo(value)
        }
    }

    @Test
    fun `representative mappings map by name`() {
        assertThat(SourceSensor.DEXCOM_G6_NATIVE.toDb()).isEqualTo(GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE)
        assertThat(GlucoseValue.SourceSensor.LIBRE_2_NATIVE.fromDb()).isEqualTo(SourceSensor.LIBRE_2_NATIVE)
        assertThat(SourceSensor.UNKNOWN.toDb()).isEqualTo(GlucoseValue.SourceSensor.UNKNOWN)
        assertThat(GlucoseValue.SourceSensor.ZT_PREDICTION.fromDb()).isEqualTo(SourceSensor.ZT_PREDICTION)
    }
}
