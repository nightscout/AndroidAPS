package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TrendArrow
import app.aaps.database.entities.GlucoseValue
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TrendArrowExtensionTest {

    @Test
    fun roundTripFromDomain() {
        TrendArrow.entries.forEach {
            assertThat(it.toDb().fromDb()).isEqualTo(it)
        }
    }

    @Test
    fun roundTripFromEntity() {
        GlucoseValue.TrendArrow.entries.forEach {
            assertThat(it.fromDb().toDb()).isEqualTo(it)
        }
    }

    @Test
    fun representativeMappings() {
        assertThat(TrendArrow.FLAT.toDb()).isEqualTo(GlucoseValue.TrendArrow.FLAT)
        assertThat(TrendArrow.NONE.toDb()).isEqualTo(GlucoseValue.TrendArrow.NONE)
        assertThat(TrendArrow.TRIPLE_UP.toDb()).isEqualTo(GlucoseValue.TrendArrow.TRIPLE_UP)
        assertThat(TrendArrow.TRIPLE_DOWN.toDb()).isEqualTo(GlucoseValue.TrendArrow.TRIPLE_DOWN)

        assertThat(GlucoseValue.TrendArrow.SINGLE_UP.fromDb()).isEqualTo(TrendArrow.SINGLE_UP)
        assertThat(GlucoseValue.TrendArrow.DOUBLE_DOWN.fromDb()).isEqualTo(TrendArrow.DOUBLE_DOWN)
        assertThat(GlucoseValue.TrendArrow.FORTY_FIVE_UP.fromDb()).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }
}
