package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.AppExit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.seconds

/**
 * Covers the PR2 ConfigBuilder ↔ ActivePlugin-key bridge write path: a plugin switch mirrors the new
 * selection (by [PluginBase.pluginId]) into the synthetic synced key, and the write is suppressed when
 * the key already holds that value (the no-op guard that prevents spurious publishes / echo). The async
 * observer-adoption direction is left to integration/device coverage.
 */
internal class ConfigBuilderImplTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var appExit: AppExit
    @Mock private lateinit var config: Config
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var sens2: PluginBase
    @Mock private lateinit var sens2Desc: PluginDescription

    private lateinit var sut: ConfigBuilderImpl
    private var sensEnabled = false   // stateful so setPluginEnabled is reflected by isEnabled

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)   // skip the UEL block in performPluginSwitch

        whenever(sens2.getType()).thenReturn(PluginType.SENSITIVITY)
        whenever(sens2.pluginId).thenReturn("Sens2")
        whenever(sens2.pluginDescription).thenReturn(sens2Desc)
        whenever(sens2.enforcedState()).thenReturn(null)   // no enforcement: the stored choice decides
        whenever(sens2.isEnabled(PluginType.SENSITIVITY)).thenAnswer { sensEnabled }
        whenever(sens2.isEnabled()).thenAnswer { sensEnabled }
        // Returns the start/stop job now, so a caller can wait for the plugin to really start or stop.
        // Null here: this stub changes the flag and nothing else has to be waited for.
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenAnswer { sensEnabled = it.getArgument(1); null }

        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsListByInterface(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsList(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenReturn("")

        sut = ConfigBuilderImpl(aapsLogger, rh, preferences, rxBus, activePlugin, uel, pumpSync, appExit, config, persistenceLayer)
    }

    // ---- calibrations after a BG source switch ----

    private val sessionStart = 1_000_000L
    private val sessionCalibrations = listOf(
        CAL(id = 7, timestamp = sessionStart + 10_000, fingerstickMgdl = 120.0, sensorMgdlAtPairing = 110.0),
        CAL(id = 8, timestamp = sessionStart + 20_000, fingerstickMgdl = 90.0, sensorMgdlAtPairing = 95.0)
    )

    /**
     * The active BG source before the switch, then after it. As in `PluginStore`, the active source changes only when
     * the selection is verified, not when a plugin is enabled. A check made before that sees the old source.
     */
    private fun bgSourceSwitch(before: BgSource, after: BgSource) {
        var active = before
        whenever(activePlugin.activeBgSource).thenAnswer { active }
        whenever(activePlugin.verifySelectionInCategories()).thenAnswer { active = after; emptyList<Job>() }
        runBlocking {
            whenever(persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE))
                .thenReturn(TE(timestamp = sessionStart, type = TE.Type.SENSOR_CHANGE, glucoseUnit = GlucoseUnit.MGDL))
            whenever(persistenceLayer.getValidCalibrationEntriesSince(sessionStart)).thenReturn(sessionCalibrations)
        }
    }

    /** The pairs were taken with the old source: they would correct the new one wrongly, so they are marked invalid. */
    @Test
    fun aBgSourceSwitchResetsTheCalibrationsOfTheSession() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        bgSourceSwitch(before = mock(), after = mock())

        sut.performPluginSwitch(sens2, true, PluginType.BGSOURCE)

        for (entry in sessionCalibrations)
            verifyBlocking(persistenceLayer, timeout(2_000)) { invalidateCalibrationEntry(eq(entry.id), eq(Action.CALIBRATION_REMOVED), eq(Sources.ConfigBuilder), anyOrNull(), any()) }
    }

    /** Selecting the source that is already active changes nothing. */
    @Test
    fun theSameBgSourceKeepsTheCalibrations() {
        whenever(config.AAPSCLIENT).thenReturn(false)
        val source: BgSource = mock()
        bgSourceSwitch(before = source, after = source)

        sut.performPluginSwitch(sens2, true, PluginType.BGSOURCE)

        verifyBlocking(persistenceLayer, after(300).never()) { invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any()) }
    }

    /** The calibrations belong to the master's source. A client only receives them, invalid or not, through Nightscout. */
    @Test
    fun aClientDoesNotResetCalibrations() {
        bgSourceSwitch(before = mock(), after = mock())

        sut.performPluginSwitch(sens2, true, PluginType.BGSOURCE)

        verifyBlocking(persistenceLayer, after(300).never()) { invalidateCalibrationEntry(any(), any(), any(), anyOrNull(), any()) }
    }

    /** A local plugin switch mirrors the new selection (by pluginId) into the synced ActivePlugin key. */
    @Test
    fun performPluginSwitchMirrorsSelectionIntoKey() {
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences).put(StringNonKey.ActivePluginSensitivity, "Sens2")
    }

    /** No-op guard: when the key already holds the current selection, the mirror write is skipped. */
    @Test
    fun syncSkipsNoOpWriteWhenUnchanged() {
        whenever(preferences.get(StringNonKey.ActivePluginSensitivity)).thenReturn("Sens2")  // already current
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences, never()).put(eq(StringNonKey.ActivePluginSensitivity), any<String>())
    }

    /**
     * SSOT guard: PluginType.singleSelect / selectionSyncs are the single source of truth, and the
     * activePluginKey wiring must agree with them for EVERY type — a single-select type has exactly one
     * ActivePlugin key, and a selection-syncing type's key carries a SyncSpec. Fails the build if either
     * drifts (e.g. a new synced category whose key wasn't flagged, or a flag flipped without the key).
     */
    @Test
    fun activePluginKeyWiringMatchesPluginTypeFlags() {
        PluginType.entries.forEach { type ->
            assertEquals(type.singleSelect, sut.activePluginKey(type) != null, "singleSelect mismatch for $type")
            assertEquals(type.selectionSyncs, sut.activePluginKey(type)?.sync != null, "selectionSyncs mismatch for $type")
        }
    }

    // ---- applyConfiguration ----

    /** One observer per synced key, however many times the configuration has been applied. */
    private val observedKeys get() = sut.syncedSelectionTypes.count { sut.activePluginKey(it) != null }

    private fun stubKeyObservation(flow: MutableStateFlow<String>) {
        whenever(preferences.observe(any<StringNonPreferenceKey>())).thenReturn(flow)
    }

    /**
     * Applying the configuration on a live app must not leave a second set of observers behind.
     *
     * `initialize` used to launch a collector per synced key with nothing cancelling the previous
     * ones, which was harmless while only startup called it. It is not harmless now that applying
     * imported settings calls it too: every later selection change - including one pushed by the
     * master - would run `performPluginSwitch` once more for each import done in that session, and
     * nothing would say so.
     */
    @Test
    fun `applying the configuration twice leaves one observer per key`() = runBlocking {
        val keyFlow = MutableStateFlow("")
        stubKeyObservation(keyFlow)

        sut.applyConfiguration()
        sut.applyConfiguration()

        withTimeout(5.seconds) { keyFlow.subscriptionCount.first { it == observedKeys } }
        // ...and it stays there. Without the cancel, the second set subscribes on top of the first and
        // the count settles at twice this instead.
        delay(200)
        assertThat(keyFlow.subscriptionCount.value).isEqualTo(observedKeys)
    }

    /**
     * The difference between this and `initialize`: it returns only once the plugins it started and
     * stopped have finished doing so.
     *
     * `setPluginEnabled` sets the flag now but only *schedules* `onStart` / `onStop`. Returning before
     * those have run would give away the whole point of applying with the pump held - the caller would
     * let commands flow again while a pump driver was still being torn down.
     */
    @Test
    fun `applying the configuration waits for the plugins to start`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))
        whenever(preferences.getIfExists(eq(BooleanComposedKey.ConfigBuilderEnabled), any())).thenReturn(true)
        val stillStarting = Job()
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenReturn(stillStarting)

        val applying = launch { sut.applyConfiguration() }
        delay(200)
        assertThat(applying.isCompleted).isFalse()   // the plugin has not finished starting

        stillStarting.complete()
        withTimeout(5.seconds) { applying.join() }
    }

    /**
     * The other half of the same contract, and the one that was missing (plan bug 12).
     *
     * `initialize` cannot wait for the plugins itself - it is not `suspend`, and several instrumented
     * tests call it - so it has to hand the jobs back instead. It used to drop them, which is how the
     * two start paths came to give different guarantees: `applyConfiguration` returned with the plugins
     * started, `initialize` returned with them merely marked enabled. Whatever the caller does next then
     * runs against half-started plugins, and on the startup path the very next statement
     * (`RunningModeReconciler.start()`) reads the active pump.
     */
    @Test
    fun `initialize hands back the jobs its plugins are starting on`() {
        stubKeyObservation(MutableStateFlow(""))
        whenever(preferences.getIfExists(eq(BooleanComposedKey.ConfigBuilderEnabled), any())).thenReturn(true)
        val stillStarting = Job()
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenReturn(stillStarting)

        val jobs = sut.initialize()

        assertThat(jobs).contains(stillStarting)
    }

    /**
     * Applying on a live app must announce the change, or nothing on screen follows it.
     *
     * Device-found: importing a file that selected a different pump switched the plugin correctly -
     * the log showed "Starting: Virtual Pump" / "Stopping: DanaR" - but every screen went on drawing
     * the old pump, because `loadPref` flips plugin state directly and skips everything
     * `performPluginSwitch` emits. `initialize` never needed these; nothing is on screen at startup.
     */
    @Test
    fun `applying the configuration announces the change`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))

        sut.applyConfiguration()

        verify(rxBus).send(any<EventConfigBuilderChange>())
    }

    /** The Configuration screen refreshes off this one rather than the raw key flow. */
    @Test
    fun `applying the configuration signals an active-selection change`() = runBlocking {
        stubKeyObservation(MutableStateFlow(""))
        // The flow has no replay, so the collector has to be subscribed before the emit or it misses it.
        val seen = CompletableDeferred<Unit>()
        val collector = launch { sut.activeSelectionChanges.collect { seen.complete(Unit) } }
        delay(200)

        sut.applyConfiguration()

        withTimeout(5.seconds) { seen.await() }
        collector.cancel()
    }

    /** Startup keeps the old behaviour: it schedules the plugin starts and does not wait for them. */
    @Test
    fun `initialize does not wait for the plugins to start`() {
        stubKeyObservation(MutableStateFlow(""))
        whenever(preferences.getIfExists(eq(BooleanComposedKey.ConfigBuilderEnabled), any())).thenReturn(true)
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenReturn(Job())   // never completes

        sut.initialize()   // returns, rather than hanging on a plugin that is still starting
    }
}
