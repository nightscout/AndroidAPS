package app.aaps.plugins.smoothing

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import kotlin.math.abs
import kotlin.math.sqrt

class AdaptiveSmoothingPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var sp: SP

    private lateinit var plugin: AdaptiveSmoothingPlugin

    @BeforeEach
    fun setUpPlugin() {
        whenever(sp.getDouble(eq("ukf_learned_r"), any())).thenReturn(25.0)
        whenever(sp.getLong(eq("ukf_last_processed_timestamp"), any())).thenReturn(0L)
        whenever(sp.getLong(eq("ukf_sensor_change_timestamp"), any())).thenReturn(0L)

        whenever(persistenceLayer.observeChanges(eq(TE::class.java))).thenReturn(emptyFlow())
        whenever { persistenceLayer.getTherapyEventDataFromTime(any(), any()) } doReturn emptyList()

        whenever { iobCobCalculator.calculateIobFromBolus() } doReturn IobTotal(time = 0L, iob = 0.2)
        whenever { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() } doReturn IobTotal(time = 0L, iob = 0.2)
        whenever(preferences.get(BooleanKey.OApsAIMInight)).thenReturn(true)

        plugin = AdaptiveSmoothingPlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            rxBus = rxBus,
            aapsSchedulers = aapsSchedulers,
            persistenceLayer = persistenceLayer,
            sp = sp,
            iobCobCalculator = iobCobCalculator,
            preferences = preferences
        )
    }

    @Test
    fun `extreme sensor noise is attenuated and output remains clinically bounded`() {
        val series = cgmSeries(
            118.0, 165.0, 85.0, 170.0, 80.0, 162.0, 88.0, 168.0, 84.0, 160.0,
            90.0, 158.0, 95.0, 150.0, 100.0, 145.0
        )

        val smoothed = plugin.smooth(series)

        val rawStd = stdDev(smoothed.map { it.value })
        val smoothStd = stdDev(smoothed.map { it.smoothed!! })

        assertThat(smoothStd).isLessThan(rawStd * 0.80)
        assertThat(smoothed.all { it.smoothed!!.isFinite() && it.smoothed!! in 39.0..500.0 }).isTrue()
        assertThat(plugin.lastAdaptiveSmoothingQualitySnapshot()).isNotNull()
    }

    @Test
    fun `compression low artifact is blocked when drop is implausible and low iob`() {
        whenever { iobCobCalculator.calculateIobFromBolus() } doReturn IobTotal(time = 0L, iob = 0.1)
        whenever { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended() } doReturn IobTotal(time = 0L, iob = 0.1)

        val series = cgmSeries(
            118.0, 116.0, 115.0, 112.0, 108.0, 104.0,
            72.0, 54.0, 112.0, 118.0, 121.0, 119.0
        )

        val smoothed = plugin.smooth(series)
        val snapshot = plugin.lastAdaptiveSmoothingQualitySnapshot()

        val rawMin = smoothed.minOf { it.value }
        val smoothAtRawMin = smoothed.first { it.value == rawMin }.smoothed!!

        assertThat(rawMin).isAtMost(54.0)
        assertThat(smoothAtRawMin).isGreaterThan(rawMin + 20.0)
        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.compressionRate).isGreaterThan(0.0)
        assertThat(snapshot.tier).isNotEqualTo(AdaptiveSmoothingQualityTier.OK)
    }

    @Test
    fun `missing data cadence survives with stable output and no numeric failures`() {
        val start = 1_710_000_000_000L
        val sparse = mutableListOf<InMemoryGlucoseValue>()
        val oldestToNewest = listOf(110.0, 113.0, 117.0, 121.0, 124.0, 128.0, 131.0)

        oldestToNewest.forEachIndexed { index, bg ->
            sparse.add(
                InMemoryGlucoseValue(
                    timestamp = start + (index * 15L * 60L * 1000L),
                    value = bg
                )
            )
        }
        sparse.reverse()

        val smoothed = plugin.smooth(sparse)

        assertThat(smoothed.all { it.smoothed!!.isFinite() }).isTrue()
        assertThat(smoothed.maxOf { abs(it.smoothed!! - it.value) }).isLessThan(25.0)
        assertThat(plugin.lastAdaptiveSmoothingQualitySnapshot()).isNotNull()
    }

    @Test
    fun `rapid physiological rise keeps low lag and rising trend arrow`() {
        val series = cgmSeries(
            102.0, 105.0, 111.0, 124.0, 146.0, 171.0, 198.0, 226.0, 244.0, 255.0
        )

        val smoothed = plugin.smooth(series)
        val newest = smoothed.first()

        assertThat(newest.value).isEqualTo(255.0)
        assertThat(abs(newest.smoothed!! - newest.value)).isLessThan(18.0)
        assertThat(newest.trendArrow).isAnyOf(
            TrendArrow.FORTY_FIVE_UP,
            TrendArrow.SINGLE_UP,
            TrendArrow.DOUBLE_UP
        )
    }

    private fun cgmSeries(vararg oldestToNewest: Double): MutableList<InMemoryGlucoseValue> {
        val start = 1_700_000_000_000L
        val values = oldestToNewest.mapIndexed { idx, bg ->
            InMemoryGlucoseValue(
                timestamp = start + idx * 5L * 60L * 1000L,
                value = bg
            )
        }.toMutableList()
        values.reverse() // plugin expects newest -> oldest
        return values
    }

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
}
