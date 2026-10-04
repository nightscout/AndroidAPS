package app.aaps.plugins.sync.wear

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WearPluginTest : TestBaseWithProfile() {

    @Mock lateinit var dataHandlerMobile: DataHandlerMobile
    @Mock lateinit var dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var scenes: SceneAutomationApi

    private lateinit var wearPlugin: WearPlugin
    private lateinit var rateLimit: RateLimit

    private val temporaryTargets = MutableSharedFlow<List<TT>>()
    private val runningModes = MutableSharedFlow<List<RM>>()

    @BeforeEach fun prepare() {
        rateLimit = RateLimit(dateUtil)
        wearPlugin = WearPlugin(aapsLogger, rh, preferences, rxBus, context, dataHandlerMobile, dataLayerListenerServiceMobileHelper, config, BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined)), persistenceLayer, scenes, mock())
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(temporaryTargets)
        whenever(persistenceLayer.observeChanges(RM::class)).thenReturn(runningModes)
    }

    /** Collects [WearPlugin.resendRequests] in virtual time; the database flows send their first value as on a phone. */
    private suspend fun TestScope.collectResends(): MutableList<String> {
        val reasons = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { wearPlugin.resendRequests().collect { reasons.add(it) } }
        runCurrent()
        // observeChanges sends the current state when it is collected; that is not a change
        temporaryTargets.emit(emptyList())
        runningModes.emit(emptyList())
        return reasons
    }

    /** Sends [send] at [atSecond] seconds after the previous call's time. */
    private fun TestScope.at(atSecond: Double, send: () -> Unit) {
        advanceTimeBy((atSecond * 1000).toLong().milliseconds - testScheduler.currentTime.milliseconds)
        runCurrent()
        send()
    }

    private val autosens = { rxBus.send(EventAutosensCalculationFinished(triggeredByNewBG = true)) }
    private val loopGui = { rxBus.send(EventLoopUpdateGui()) }
    private val nsStatus = { rxBus.send(EventNsClientStatusUpdated()) }

    /**
     * The events of the BG cycle measured on a Pixel at 19:15 (seconds after 19:15:00). Each event
     * used to start a full resend: 10 events, 9 resends seen in the log. Now one per burst.
     */
    @Test
    fun `a BG cycle of events gives one resend per burst`() = runTest {
        val reasons = collectResends()

        at(10.0, autosens); at(11.0, autosens)                   // calculation, twice
        at(24.0, loopGui)                                         // loop result
        at(29.0, nsStatus)                                        // NS
        at(49.0, loopGui); at(50.0, loopGui); at(50.2, autosens)  // a new TBR: history change, calculation, loop
        at(51.0, autosens); at(51.3, loopGui)
        at(55.0, nsStatus)
        advanceTimeBy(10.seconds)
        runCurrent()

        // The reason of the last event of each burst
        assertThat(reasons).containsExactly(
            "EventAutosensCalculationFinished", "EventLoopUpdateGui", "EventNsClientStatusUpdated", "EventLoopUpdateGui", "EventNsClientStatusUpdated"
        ).inOrder()
    }

    /** A single event waits the debounce time, not longer. */
    @Test
    fun `one event is resent after the debounce time`() = runTest {
        val reasons = collectResends()

        loopGui()
        advanceTimeBy(WearPlugin.RESEND_DEBOUNCE - 100.milliseconds)
        runCurrent()
        assertThat(reasons).isEmpty()
        advanceTimeBy(200.milliseconds)
        runCurrent()
        assertThat(reasons).containsExactly("EventLoopUpdateGui")
    }

    /**
     * The temporary target and running mode changes go through the same debounce, so a TT that also
     * makes the loop run gives one resend, and the first value of the database flows gives none.
     */
    @Test
    fun `database changes share the debounce and their first value is not a change`() = runTest {
        val reasons = collectResends()
        advanceTimeBy(10.seconds)
        runCurrent()
        assertThat(reasons).isEmpty()

        temporaryTargets.emit(emptyList())
        advanceTimeBy(500.milliseconds)
        runningModes.emit(emptyList())
        advanceTimeBy(500.milliseconds)
        loopGui()
        advanceTimeBy(5.seconds)
        runCurrent()

        assertThat(reasons).containsExactly("EventLoopUpdateGui")
    }
}
