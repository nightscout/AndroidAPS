package info.nightscout.androidaps.plugins.constraints.bgQualityCheck

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import java.util.*

class BgQualityCheckPluginTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var plugin: BgQualityCheckPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }
    private val autosensDataStore = AutosensDataStore()

    @Before
    fun mock() {
        plugin = BgQualityCheckPlugin(injector, aapsLogger, rh, RxBus(aapsSchedulers, aapsLogger), iobCobCalculator, aapsSchedulers, fabricPrivacy, dateUtil)
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
    }

    @Test
    fun runTest() {
        autosensDataStore.lastUsed5minCalculation = null
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.UNKNOWN, plugin.state)
        Assert.assertEquals(0, plugin.icon())
        autosensDataStore.lastUsed5minCalculation = true
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.FIVE_MIN_DATA, plugin.state)
        Assert.assertEquals(0, plugin.icon())
        autosensDataStore.lastUsed5minCalculation = false
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.RECALCULATED, plugin.state)
        Assert.assertEquals(R.drawable.ic_baseline_warning_24_yellow, plugin.icon())

        val superData: MutableList<GlucoseValue> = ArrayList()
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(20).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(15).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(10).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(5).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        autosensDataStore.bgReadings = superData

        autosensDataStore.lastUsed5minCalculation = true
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.FIVE_MIN_DATA, plugin.state)
        autosensDataStore.lastUsed5minCalculation = false
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
        autosensDataStore.bgReadings = duplicatedData

        autosensDataStore.lastUsed5minCalculation = true
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
        autosensDataStore.bgReadings = identicalData

        autosensDataStore.lastUsed5minCalculation = false
        plugin.processBgData()
        Assert.assertEquals(BgQualityCheckPlugin.State.DOUBLED, plugin.state)
    }

    @Test
    fun isLoopInvocationAllowedTest() {
        plugin.state = BgQualityCheckPlugin.State.UNKNOWN
        Assert.assertEquals(true, plugin.isLoopInvocationAllowed(Constraint(true)).value())
        plugin.state = BgQualityCheckPlugin.State.FIVE_MIN_DATA
        Assert.assertEquals(true, plugin.isLoopInvocationAllowed(Constraint(true)).value())
        plugin.state = BgQualityCheckPlugin.State.RECALCULATED
        Assert.assertEquals(true, plugin.isLoopInvocationAllowed(Constraint(true)).value())
        plugin.state = BgQualityCheckPlugin.State.DOUBLED
        Assert.assertEquals(false, plugin.isLoopInvocationAllowed(Constraint(true)).value())
    }

}