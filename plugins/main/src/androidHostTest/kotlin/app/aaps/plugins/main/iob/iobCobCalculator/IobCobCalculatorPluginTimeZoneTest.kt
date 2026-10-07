package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.rx.events.EventTimeZoneChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds

/**
 * A time zone change must reset the IOB calculation: the basal profile is read in the current zone,
 * so every cached value of the past is calculated differently afterwards (_docs/IOB_TIME_ZONE.md).
 *
 * Runs the real plugin with its own scope, so the debounce runs in real time; it is shortened here.
 */
class IobCobCalculatorPluginTimeZoneTest {

    private val aapsLogger = AAPSLoggerTest()
    private val rxBus = mock<RxBus>()
    private val timeZoneEvents = MutableSharedFlow<EventTimeZoneChanged>(extraBufferCapacity = 16)
    private val configEvents = MutableSharedFlow<EventConfigBuilderChange>(extraBufferCapacity = 16)
    private val preferences = mock<Preferences>()
    private val persistenceLayer = mock<PersistenceLayer>()
    private val calculationWorkflow = mock<CalculationWorkflow>()
    private val dateUtil = mock<DateUtil>()
    private lateinit var sut: IobCobCalculatorPlugin

    @BeforeEach
    fun setUp() {
        whenever(rxBus.toFlow<Event>(any())).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventTimeZoneChanged::class)).thenReturn(timeZoneEvents)
        whenever(rxBus.toFlow(EventConfigBuilderChange::class)).thenReturn(configEvents)
        whenever(persistenceLayer.observeChanges<Any>(any())).thenReturn(emptyFlow())
        whenever(preferences.observe(any<IntNonPreferenceKey>())).thenReturn(MutableStateFlow(0))
        whenever(preferences.observe(any<StringNonPreferenceKey>())).thenReturn(MutableStateFlow(""))
        whenever(preferences.observe(any<DoubleNonPreferenceKey>())).thenReturn(MutableStateFlow(0.0))
        whenever(dateUtil.now()).thenReturn(1_000_000_000L)
        sut = IobCobCalculatorPlugin(
            aapsLogger, rxBus, preferences, mock<TextResolver>(), mock<ProfileFunction>(), mock<ActivePlugin>(), dateUtil,
            persistenceLayer, mock<OverviewData>(), calculationWorkflow, mock<DecimalFormatter>(), mock<ProcessedTbrEbData>(),
            mock<CalculationSignalsEmitter>(), mock<NotificationManager>()
        ) { mock<OverviewDataCache>() }
        sut.timeZoneResetDebounce = DEBOUNCE
        runBlocking { sut.onStart() }
    }

    @AfterEach
    fun tearDown() = runBlocking { sut.onStop() }

    private fun verifyResets(count: Int, reason: String = REASON) = verifyBlocking(calculationWorkflow, timeout(WAIT_MS).times(count)) {
        runCalculation(any(), any(), any(), any(), any(), eq(reason), any(), any(), any())
    }

    @Test
    fun `a time zone change resets the calculation`() {
        timeZoneEvents.tryEmit(EventTimeZoneChanged())

        verifyResets(1)
        verifyBlocking(calculationWorkflow) { stopCalculation(eq(CalculationWorkflow.MAIN_CALCULATION), eq(REASON)) }
    }

    /** Automatic detection near a border can flip the zone several times; one reset is enough. */
    @Test
    fun `a burst of time zone changes resets only once`() {
        repeat(5) {
            timeZoneEvents.tryEmit(EventTimeZoneChanged())
            Thread.sleep(DEBOUNCE.inWholeMilliseconds / 4)
        }

        verifyResets(1)
        Thread.sleep(DEBOUNCE.inWholeMilliseconds * 3)
        verifyBlocking(calculationWorkflow, times(1)) { runCalculation(any(), any(), any(), any(), any(), eq(REASON), any(), any(), any()) }
    }

    /** Changes far enough apart are separate, each needs its own reset. */
    @Test
    fun `time zone changes far apart reset each time`() {
        timeZoneEvents.tryEmit(EventTimeZoneChanged())
        verifyResets(1)

        timeZoneEvents.tryEmit(EventTimeZoneChanged())
        verifyResets(2)
    }

    /** The subscription must not answer other events with this reset. */
    @Test
    fun `other events do not trigger the time zone reset`() {
        configEvents.tryEmit(EventConfigBuilderChange())
        verifyResets(1, reason = "onEventConfigBuilderChange")

        verifyBlocking(calculationWorkflow, never()) { runCalculation(any(), any(), any(), any(), any(), eq(REASON), any(), any(), any()) }
    }

    private companion object {

        const val REASON = "onEventTimeZoneChanged"
        val DEBOUNCE = 200.milliseconds
        const val WAIT_MS = 3_000L
    }
}
