package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class TrendCalculatorImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var trendCalculator: TrendCalculatorImpl

    @BeforeEach
    fun setup() {
        trendCalculator = TrendCalculatorImpl(rh)
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_down)).thenReturn("Double Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_down)).thenReturn("Single Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_down)).thenReturn("Forty Five Down")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_flat)).thenReturn("Flat")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_forty_five_up)).thenReturn("Forty Five Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_single_up)).thenReturn("Single Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_double_up)).thenReturn("Double Up")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_none)).thenReturn("None")
        whenever(rh.gs(app.aaps.core.ui.R.string.a11y_arrow_unknown)).thenReturn("Unknown")
    }

    @Test
    fun `getTrendArrow returns null when data is null`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(null)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isNull()
    }

    @Test
    fun `getTrendArrow returns null when data is empty`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(mutableListOf())
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isNull()
    }

    @Test
    fun `getTrendArrow returns NONE when only one reading`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.NONE)
    }

    @Test
    fun `getTrendArrow returns existing arrow when value not recalculated`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 100.0, trendArrow = TrendArrow.SINGLE_UP),
            createGlucoseValue(95.0, 0L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendArrow calculates DOUBLE_DOWN for slope less than -3_5 per minute`() {
        // Need slope <= -3.5 per minute
        // Over 5 minutes: -3.5 * 5 = -17.5 mg/dL
        // Using -20 mg/dL over 5 minutes = -4 per minute
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 80.0),  // 20 mg/dL drop over 5 min = -4 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates SINGLE_DOWN for slope between -3_5 and -2 per minute`() {
        // Need -3.5 < slope <= -2 per minute
        // Over 5 minutes: -2.5 * 5 = -12.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 87.5),  // 12.5 mg/dL drop over 5 min = -2.5 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates FORTY_FIVE_DOWN for slope between -2 and -1 per minute`() {
        // Need -2 < slope <= -1 per minute
        // Over 5 minutes: -1.5 * 5 = -7.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 92.5),  // 7.5 mg/dL drop over 5 min = -1.5 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
    }

    @Test
    fun `getTrendArrow calculates FLAT for slope between -1 and 1 per minute`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 100.0),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `getTrendArrow calculates FORTY_FIVE_UP for slope between 1 and 2 per minute`() {
        // Need 1 < slope <= 2 per minute
        // Over 5 minutes: 1.5 * 5 = 7.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 107.5),  // 7.5 mg/dL rise over 5 min = 1.5 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }

    @Test
    fun `getTrendArrow calculates SINGLE_UP for slope between 2 and 3_5 per minute`() {
        // Need 2 < slope <= 3.5 per minute
        // Over 5 minutes: 2.5 * 5 = 12.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 112.5),  // 12.5 mg/dL rise over 5 min = 2.5 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendArrow calculates DOUBLE_UP for slope between 3_5 and 40 per minute`() {
        // Need 3.5 < slope <= 40 per minute
        // Over 5 minutes: 4 * 5 = 20 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 120.0),  // 20 mg/dL rise over 5 min = 4 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_UP)
    }

    @Test
    fun `getTrendArrow returns NONE for slope greater than 40 per minute`() {
        // Over 5 minutes: 50 * 5 = 250 mg/dL (slope = 50 per minute > 40)
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 350.0),  // 250 mg/dL rise over 5 min = 50 per minute
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.NONE)
    }

    @Test
    fun `getTrendArrow handles same timestamp gracefully`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 110.0),
            createGlucoseValue(100.0, 300000L, recalculated = 100.0)  // Same timestamp
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `getTrendArrow recalculates when value differs from recalculated`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 112.5, trendArrow = TrendArrow.FLAT),  // Smoothed value differs
            createGlucoseValue(95.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        // Should recalculate and get SINGLE_UP (12.5 mg/dL rise over 5 min = 2.5 per minute)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `getTrendDescription returns correct description for DOUBLE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 80.0),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Double Down")
    }

    @Test
    fun `getTrendDescription returns correct description for SINGLE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 87.5),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Single Down")
    }

    @Test
    fun `getTrendDescription returns correct description for FORTY_FIVE_DOWN`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 92.5),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Forty Five Down")
    }

    @Test
    fun `getTrendDescription returns correct description for FLAT`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 100.0),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Flat")
    }

    @Test
    fun `getTrendDescription returns correct description for FORTY_FIVE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 107.5),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Forty Five Up")
    }

    @Test
    fun `getTrendDescription returns correct description for SINGLE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 112.5),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Single Up")
    }

    @Test
    fun `getTrendDescription returns correct description for DOUBLE_UP`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 120.0),
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Double Up")
    }

    @Test
    fun `getTrendDescription returns correct description for NONE`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 1000L)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("None")
    }

    @Test
    fun `getTrendDescription handles null data`() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(null)
        assertThat(trendCalculator.getTrendDescription(autosensDataStore)).isEqualTo("Unknown")
    }

    @Test
    fun `slope boundary test at exactly -3_5 per minute`() {
        // -3.5 per minute over 5 minutes = -3.5 * 5 = -17.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 82.5),  // -17.5 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly -2 per minute`() {
        // -2 per minute over 5 minutes = -2 * 5 = -10 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 90.0),  // -10 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly -1 per minute`() {
        // -1 per minute over 5 minutes = -1 * 5 = -5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 95.0),  // -5 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_DOWN)
    }

    @Test
    fun `slope boundary test at exactly 1 per minute`() {
        // 1 per minute over 5 minutes = 1 * 5 = 5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 105.0),  // 5 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `slope boundary test at exactly 2 per minute`() {
        // 2 per minute over 5 minutes = 2 * 5 = 10 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 110.0),  // 10 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.FORTY_FIVE_UP)
    }

    @Test
    fun `slope boundary test at exactly 3_5 per minute`() {
        // 3.5 per minute over 5 minutes = 3.5 * 5 = 17.5 mg/dL
        val data = mutableListOf(
            createGlucoseValue(100.0, 300000L, recalculated = 117.5),  // 17.5 mg/dL over 5 min
            createGlucoseValue(100.0, 0L, recalculated = 100.0)
        )
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(data)
        assertThat(trendCalculator.getTrendArrow(autosensDataStore)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    private fun createGlucoseValue(
        value: Double,
        timestamp: Long,
        recalculated: Double = value,
        trendArrow: TrendArrow = TrendArrow.NONE
    ) = InMemoryGlucoseValue(
        timestamp = timestamp,
        value = value,
        trendArrow = trendArrow,
        smoothed = recalculated
    )
}
