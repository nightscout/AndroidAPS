package info.nightscout.plugins.constraints.bgQualityCheck

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.bgQualityCheck.BgQualityCheck
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.source.BgSource
import info.nightscout.interfaces.source.DexcomBoyda
import info.nightscout.plugins.constraints.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
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
    @Mock lateinit var activePlugin: ActivePlugin

    private lateinit var plugin: BgQualityCheckPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }
    private val now = 100000000L
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
                dateUtil,
                activePlugin
            )
        `when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        `when`(rh.gs(anyInt())).thenReturn("")
        `when`(rh.gs(anyInt(), any(), any())).thenReturn("")
        `when`(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun runTest() {
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(null)
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.UNKNOWN, plugin.state)
        Assertions.assertEquals(0, plugin.icon())
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.FIVE_MIN_DATA, plugin.state)
        Assertions.assertEquals(0, plugin.icon())
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.RECALCULATED, plugin.state)
        Assertions.assertEquals(R.drawable.ic_baseline_warning_24_yellow, plugin.icon())

        val superData: MutableList<GlucoseValue> = ArrayList()
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(20).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(15).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(10).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        superData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = T.mins(5).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(superData)

        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(true)
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.FIVE_MIN_DATA, plugin.state)
        `when`(autosensDataStore.lastUsed5minCalculation).thenReturn(false)
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.RECALCULATED, plugin.state)

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
        Assertions.assertEquals(BgQualityCheck.State.DOUBLED, plugin.state)
        Assertions.assertEquals(R.drawable.ic_baseline_warning_24_red, plugin.icon())

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
        Assertions.assertEquals(BgQualityCheck.State.DOUBLED, plugin.state)

        // Flat data
        val flatData: MutableList<GlucoseValue> = ArrayList()
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(0).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow
            .FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-5).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 101.0, timestamp = now + T.mins(-10).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-15).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-20).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-25).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 99.0, timestamp = now + T.mins(-30).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-35).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-40).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-45).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(flatData)
        `when`(iobCobCalculator.ads.lastBg()).thenReturn(InMemoryGlucoseValue(flatData[0]))

        // Test non-dexcom plugin on flat data
        class OtherPlugin : BgSource {

            override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean = true
        }
        `when`(activePlugin.activeBgSource).thenReturn(OtherPlugin())
        plugin.processBgData()
        Assertions.assertEquals(BgQualityCheck.State.FLAT, plugin.state)
        Assertions.assertEquals(R.drawable.ic_baseline_trending_flat_24, plugin.icon())

        // Test dexcom plugin on flat data
        class DexcomPlugin : BgSource, DexcomBoyda {

            override fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean = true
            override fun isEnabled(): Boolean = false
            override fun requestPermissionIfNeeded() {}
            override fun findDexcomPackageName(): String? = null
        }
        `when`(activePlugin.activeBgSource).thenReturn(DexcomPlugin())
        plugin.processBgData()
        Assertions.assertNotEquals(BgQualityCheck.State.FLAT, plugin.state)

        // not enough data
        val incompleteData: MutableList<GlucoseValue> = ArrayList()
        incompleteData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(0).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        incompleteData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-5).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        `when`(autosensDataStore.getBgReadingsDataTableCopy()).thenReturn(incompleteData)
        `when`(iobCobCalculator.ads.lastBg()).thenReturn(InMemoryGlucoseValue(incompleteData[0]))
        `when`(activePlugin.activeBgSource).thenReturn(OtherPlugin())
        plugin.processBgData()// must be more than 5 values
        Assertions.assertNotEquals(BgQualityCheck.State.FLAT, plugin.state)
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 101.0, timestamp = now + T.mins(-10).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-15).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-20).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-25).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 99.0, timestamp = now + T.mins(-30).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-35).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        flatData.add(GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = now + T.mins(-40).msecs(), sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        plugin.processBgData() // must be at least 45 min old
        Assertions.assertNotEquals(BgQualityCheck.State.FLAT, plugin.state)
    }

    @Test
    fun applyMaxIOBConstraintsTest() {
        plugin.state = BgQualityCheck.State.UNKNOWN
        Assertions.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheck.State.FIVE_MIN_DATA
        Assertions.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheck.State.RECALCULATED
        Assertions.assertEquals(10.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
        plugin.state = BgQualityCheck.State.DOUBLED
        Assertions.assertEquals(0.0, plugin.applyMaxIOBConstraints(Constraint(10.0)).value(), 0.001)
    }

}