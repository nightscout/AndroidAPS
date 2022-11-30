package info.nightscout.plugins.constraints.bgQualityCheck

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.plugins.support.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when`

class BgQualityCheckPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var plugin: BgQualityCheckPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }
    //private val autosensDataStore = AutosensDataStoreObject()

    @BeforeEach
    fun mock() {
        plugin =
            BgQualityCheckPlugin(
                injector,
                aapsLogger,
                rh,
                RxBus(aapsSchedulers, aapsLogger),
                iobCobCalculator,
                aapsSchedulers,
                fabricPrivacy,
                dateUtil
            )
        `when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        `when`(rh.gs(anyInt())).thenReturn("")
        `when`(rh.gs(anyInt(), any(), any())).thenReturn("")
    }

    @Test
    fun runTest() {
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(null)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.UNKNOWN, plugin.state)
        Assert.assertEquals(0, plugin.icon())
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.FIVE_MIN_DATA, plugin.state)
        Assert.assertEquals(0, plugin.icon())
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.RECALCULATED, plugin.state)
        Assert.assertEquals(R.drawable.ic_baseline_warning_24_yellow, plugin.icon())

        val superData: MutableList<GlucoseValue> = ArrayList()
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(20).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(15).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(10).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(5).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(superData)

        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.FIVE_MIN_DATA, plugin.state)
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.RECALCULATED, plugin.state)

        val duplicatedData: MutableList<GlucoseValue> = ArrayList()
        duplicatedData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs() + 1,
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        duplicatedData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(duplicatedData)

        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.DOUBLED, plugin.state)
        Assert.assertEquals(R.drawable.ic_baseline_warning_24_red, plugin.icon())

        val identicalData: MutableList<GlucoseValue> = ArrayList()
        identicalData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        identicalData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        identicalData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        identicalData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        identicalData.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(identicalData)

        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.DOUBLED, plugin.state)
    }

    @Test
    fun applyMaxIOBConstraintsTest() {
        plugin.state = BgQualityCheckPlugin.State.UNKNOWN
        Assert.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheckPlugin.State.FIVE_MIN_DATA
        Assert.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheckPlugin.State.RECALCULATED
        Assert.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheckPlugin.State.DOUBLED
        Assert.assertEquals(0.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
    }

}