package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import javax.inject.Provider

class IobCobCalculatorPluginThrottleTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var calculationWorkflow: CalculationWorkflow
    @Mock lateinit var decimalFormatter: DecimalFormatter
    @Mock lateinit var processedTbrEbData: ProcessedTbrEbData
    @Mock lateinit var signals: CalculationSignalsEmitter
    @Mock lateinit var cache: Provider<OverviewDataCache>
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: IobCobCalculatorPlugin

    private fun getLastBgCalcTriggeredAt(): Long {
        val field = IobCobCalculatorPlugin::class.java.getDeclaredField("lastBgCalcTriggeredAt")
        field.isAccessible = true
        return field.getLong(sut)
    }

    private fun setLastBgCalcTriggeredAt(value: Long) {
        val field = IobCobCalculatorPlugin::class.java.getDeclaredField("lastBgCalcTriggeredAt")
        field.isAccessible = true
        field.setLong(sut, value)
    }

    @BeforeEach
    fun setup() {
        sut = IobCobCalculatorPlugin(
            aapsLogger, aapsSchedulers, rxBus, preferences, rh,
            profileFunction, activePlugin, fabricPrivacy, dateUtil,
            persistenceLayer, overviewData, calculationWorkflow,
            decimalFormatter, processedTbrEbData, signals, cache
        )
    }

    @Test
    fun `skips throttle check when interval is disabled`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(0)
        val priorTimestamp = now - 1000L
        setLastBgCalcTriggeredAt(priorTimestamp)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(priorTimestamp)
    }

    @Test
    fun `skips throttle check for non-BG events with 3 minute interval`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        val lastCalcTime = now - 1000L
        setLastBgCalcTriggeredAt(lastCalcTime)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = false, triggeredByNewBG = false)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(lastCalcTime)
    }

    @Test
    fun `does not throttle on first BG event with interval of 3 minutes`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        val priorTimestamp = 0L

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(now)
    }

    @Test
    fun `does not throttle when 4 minutes have elapsed with 3 minute interval`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(now - 4 * 60 * 1000L)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(now)
    }

    @Test
    fun `does not throttle when 2 minutes 55 seconds have elapsed with 3 minute interval including grace period`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(now - (2 * 60 * 1000L + 55 * 1000L))

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(now)
    }

    @Test
    fun `throttles when BG event arrives immediately with 3 minute interval`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        val priorTimestamp = now
        setLastBgCalcTriggeredAt(priorTimestamp)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(priorTimestamp)
    }

    @Test
    fun `throttles when BG events occur 30 seconds apart with 1 minute interval`() {
        val now = 1_700_000_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(1)
        val lastCalcTime = now - 30_000L
        setLastBgCalcTriggeredAt(lastCalcTime)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(lastCalcTime)
    }
}
