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
    private var fakeNow: Long = 0L

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
        fakeNow = 1_700_000_000_000L
        whenever(dateUtil.now()).thenAnswer { fakeNow }
    }

    // --- Throttle disabled (interval = 0) ---

    @Test
    fun `throttle disabled - BG-triggered event does not update lastBgCalcTriggeredAt`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(0)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(0L)
    }

    // --- Throttle enabled, BG-triggered ---

    @Test
    fun `first BG-triggered event passes through and updates timestamp`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(fakeNow)
    }

    @Test
    fun `second BG-triggered event within interval is throttled`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(fakeNow) // just ran

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        // unchanged = early-return (throttled) before scheduling
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(fakeNow)
    }

    @Test
    fun `BG-triggered event after interval passes through`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(fakeNow - 4 * 60 * 1000L) // 4 min ago

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(fakeNow)
    }

    @Test
    fun `10s grace allows slightly early BG-triggered event`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        // 2m55s ago — within the 10s grace window of a 3-minute interval
        setLastBgCalcTriggeredAt(fakeNow - (2 * 60 * 1000L + 55 * 1000L))

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(fakeNow)
    }

    // --- Non-BG events bypass throttle ---

    @Test
    fun `non-BG event bypasses throttle even within interval`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        val oldStamp = fakeNow - 1000L
        setLastBgCalcTriggeredAt(oldStamp) // just ran (BG-wise)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = false, triggeredByNewBG = false)

        // throttle didn't fire (triggeredByNewBG=false), so lastBgCalcTriggeredAt unchanged
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(oldStamp)
    }

    // --- Edge cases ---

    @Test
    fun `1 minute interval blocks rapid BG-triggered events`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(1)
        val oldStamp = fakeNow - 30_000L // 30s ago
        setLastBgCalcTriggeredAt(oldStamp)

        sut.scheduleHistoryDataChange(oldDataTimestamp = 1000L, reloadBgData = true, triggeredByNewBG = true)

        // 30s < 50s (1 min - 10s grace), so throttled
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(oldStamp)
    }
}
