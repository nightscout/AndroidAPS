package app.aaps.plugins.constraints.bgQualityCheck

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.fromGv
import app.aaps.plugins.constraints.R
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.whenever

class BgQualityCheckPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var plugin: BgQualityCheckPlugin

    private val now = 100000000L
    //private val autosensDataStore = AutosensDataStoreObject()

    @BeforeEach
    fun mock() {
        plugin =
            BgQualityCheckPlugin(aapsLogger, rh, rxBus, iobCobCalculator, aapsSchedulers, fabricPrivacy, dateUtil)
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
        whenever(rh.gs(anyInt())).thenReturn("")
        whenever(rh.gs(anyInt(), any(), any())).thenReturn("")
        whenever(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun runTest() {
        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(null)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.UNKNOWN)
        assertThat(plugin.icon()).isEqualTo(0)
        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.FIVE_MIN_DATA)
        assertThat(plugin.icon()).isEqualTo(0)
        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.RECALCULATED)
        assertThat(plugin.icon()).isEqualTo(R.drawable.ic_baseline_warning_24_yellow)

        val superData: MutableList<GV> = ArrayList()
        superData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        superData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        superData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        superData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(superData)

        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.FIVE_MIN_DATA)
        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.RECALCULATED)

        val duplicatedData: MutableList<GV> = ArrayList()
        duplicatedData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs() + 1,
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(duplicatedData)

        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.DOUBLED)
        assertThat(plugin.icon()).isEqualTo(R.drawable.ic_baseline_warning_24_red)

        val identicalData: MutableList<GV> = ArrayList()
        identicalData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        identicalData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        identicalData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        identicalData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        identicalData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(identicalData)

        whenever(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.DOUBLED)

        // Flat data Libre
        val flatData: MutableList<GV> = ArrayList()
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(0).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-5).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 101.0,
                timestamp = now + T.mins(-10).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-15).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-25).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 99.0,
                timestamp = now + T.mins(-30).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-35).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-40).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-45).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(flatData)
        whenever(iobCobCalculator.ads.lastBg()).thenReturn(InMemoryGlucoseValue.fromGv(flatData[0]))

        plugin.processBgData()
        assertThat(plugin.state).isEqualTo(BgQualityCheck.State.FLAT)
        assertThat(plugin.icon()).isEqualTo(R.drawable.ic_baseline_trending_flat_24)

        // Flat data Libre
        val flatDataDexcom: MutableList<GV> = ArrayList()
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(0).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-5).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 101.0,
                timestamp = now + T.mins(-10).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-15).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-20).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-25).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 99.0,
                timestamp = now + T.mins(-30).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-35).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-40).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatDataDexcom.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-45).msecs(),
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(flatDataDexcom)
        whenever(iobCobCalculator.ads.lastBg()).thenReturn(InMemoryGlucoseValue.fromGv(flatDataDexcom[0]))

        plugin.processBgData()
        assertThat(plugin.state).isNotEqualTo(BgQualityCheck.State.FLAT)
        assertThat(plugin.icon()).isNotEqualTo(R.drawable.ic_baseline_trending_flat_24)

        // not enough data
        val incompleteData: MutableList<GV> = ArrayList()
        incompleteData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(0).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        incompleteData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-5).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        whenever(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(incompleteData)
        whenever(iobCobCalculator.ads.lastBg()).thenReturn(InMemoryGlucoseValue.fromGv(incompleteData[0]))
        plugin.processBgData()// must be more than 5 values
        assertThat(plugin.state).isNotEqualTo(BgQualityCheck.State.FLAT)
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 101.0,
                timestamp = now + T.mins(-10).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-15).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-20).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-25).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 99.0,
                timestamp = now + T.mins(-30).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-35).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        flatData.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = now + T.mins(-40).msecs(),
                sourceSensor = SourceSensor.LIBRE_1_OTHER,
                trendArrow = TrendArrow.FLAT
            )
        )
        plugin.processBgData() // must be at least 45 min old
        assertThat(plugin.state).isNotEqualTo(BgQualityCheck.State.FLAT)
    }

    @Test
    fun applyMaxIOBConstraintsTest() {
        plugin.state = BgQualityCheck.State.UNKNOWN
        assertThat(plugin.applyMaxIOBConstraints(ConstraintObject(10.0, aapsLogger)).value()).isWithin(0.001).of(10.0)
        plugin.state = BgQualityCheck.State.FIVE_MIN_DATA
        assertThat(plugin.applyMaxIOBConstraints(ConstraintObject(10.0, aapsLogger)).value()).isWithin(0.001).of(10.0)
        plugin.state = BgQualityCheck.State.RECALCULATED
        assertThat(plugin.applyMaxIOBConstraints(ConstraintObject(10.0, aapsLogger)).value()).isWithin(0.001).of(10.0)
        plugin.state = BgQualityCheck.State.DOUBLED
        assertThat(plugin.applyMaxIOBConstraints(ConstraintObject(10.0, aapsLogger)).value()).isWithin(0.001).of(0.0)
    }

}
