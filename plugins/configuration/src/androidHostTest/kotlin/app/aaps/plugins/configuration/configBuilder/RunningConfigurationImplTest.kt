package app.aaps.plugins.configuration.configBuilder

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.scenes.ActiveSceneSnapshot
import app.aaps.core.interfaces.scenes.ActiveSceneSync
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.nssdk.localmodel.configuration.NSActiveScene
import app.aaps.core.nssdk.localmodel.configuration.NSEatReminder
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Guards the cold/hot settings-doc split: applying the cold config doc must never touch the
 * active-scene state, because the cold doc carries no `activeScene` and the apply used to clear
 * it unconditionally. Only the hot doc ([RunningConfigurationImpl.applyHot]) owns scene state.
 */
internal class RunningConfigurationImplTest {

    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var activeSceneSync: ActiveSceneSync
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var config: Config
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var notificationManager: NotificationManager
    @Mock private lateinit var nsClientRepository: NSClientRepository
    @Mock private lateinit var constraintsChecker: ConstraintsChecker
    @Mock private lateinit var eatReminder: EatReminder

    private val eatReminderState = MutableStateFlow<EatReminderState?>(null)

    private lateinit var sut: RunningConfigurationImpl

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(eatReminder.state).thenReturn(eatReminderState)
        sut = RunningConfigurationImpl(
            activePlugin, activeSceneSync,
            preferences, aapsLogger, config, pumpSync, notificationManager, nsClientRepository, constraintsChecker, eatReminder
        )
    }

    /** Empty cold doc — nothing to apply, and crucially the active scene and the eat timer are left alone. */
    @Test
    fun applyColdEmptyDoesNotTouchActiveScene() {
        sut.applyCold(NSRunningConfiguration())
        verify(activeSceneSync, never()).applyActiveScene(anyOrNull())
        verify(eatReminder, never()).applyRemote(anyOrNull())
    }

    /** The master's "time to eat" timer rides the hot doc, so a client can show its bar. */
    @Test
    fun theHotDocCarriesTheEatReminder() {
        eatReminderState.value = EatReminderState(startedAt = 1_000L, endsAt = 1_201_000L, durationMs = 1_200_000L)

        val wire = sut.hotConfiguration()["eatReminder"]?.jsonObject

        assertEquals(JsonPrimitive(1_000L), wire?.get("startedAt"))
        assertEquals(JsonPrimitive(1_201_000L), wire?.get("endsAt"))
        assertEquals(JsonPrimitive(1_200_000L), wire?.get("durationMs"))
    }

    @Test
    fun theHotDocHasNoEatReminderWithoutATimer() {
        assertNull(sut.hotConfiguration()["eatReminder"])
    }

    /** A change of the timer publishes the hot doc, like a scene change. */
    @Test
    fun theEatReminderIsAHotKey() {
        assertTrue(StringNonKey.EatReminder in sut.hotKeys())
    }

    @Test
    fun applyHotTakesTheTimerOfTheMaster() {
        sut.applyHot(NSRunningConfiguration(eatReminder = NSEatReminder(startedAt = 1_000L, endsAt = 1_201_000L, durationMs = 1_200_000L)))
        verify(eatReminder).applyRemote(eq(EatReminderState(startedAt = 1_000L, endsAt = 1_201_000L, durationMs = 1_200_000L)))
    }

    /** No timer in the hot doc ends the client's copy: the master stopped it, or an older master has none. */
    @Test
    fun applyHotWithoutATimerEndsTheCopy() {
        sut.applyHot(NSRunningConfiguration())
        verify(eatReminder).applyRemote(isNull())
    }

    /**
     * Scene *definitions* now ride the cold `syncedPrefs` block (StringNonKey.SceneDefinitions, Bidirectional).
     * Adopting them must NOT clear the running scene — definitions (cold) and active-scene state (hot) are
     * independent; SceneRepository.scenesFlow reloads from the key.
     */
    @Test
    fun applyColdWithSceneDefinitionsDoesNotTouchActiveScene() {
        whenever(preferences.get(StringNonKey.SceneDefinitions.key)).thenReturn(StringNonKey.SceneDefinitions)
        sut.applyCold(NSRunningConfiguration(syncedPrefs = mapOf(StringNonKey.SceneDefinitions.key to "[]")))
        verify(preferences).putRemote(StringNonKey.SceneDefinitions, "[]", 0L)
        verify(activeSceneSync, never()).applyActiveScene(anyOrNull())
    }

    /** The hot doc owns scene state: a null activeScene clears it (master-side dismissal propagates). */
    @Test
    fun applyHotWithNullActiveSceneClearsIt() {
        sut.applyHot(NSRunningConfiguration(activeScene = null))
        verify(activeSceneSync, times(1)).applyActiveScene(isNull())
    }

    /**
     * Whether autosens is used is worked out from cold settings, so it goes in the cold doc. In the hot doc, which is
     * sent only when the scene changes, switching autosens off on the master did not reach the client.
     */
    @Test
    fun theColdDocCarriesWhetherAutosensIsUsedAndTheHotDocDoesNot() {
        val pump = mock<PumpWithConcentration>()
        whenever(config.appInitialized).thenReturn(true)
        whenever(config.VERSION_NAME).thenReturn("3.4.0")
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.isInitialized()).thenReturn(true)
        whenever(pump.model()).thenReturn(PumpType.GENERIC_AAPS)
        val autosens = mock<Constraint<Boolean>>().also { whenever(it.value()).thenReturn(false) }
        whenever(constraintsChecker.isAutosensModeEnabled()).thenReturn(autosens)

        assertEquals(JsonPrimitive(false), sut.configuration()["usedAutosensOnMainPhone"])
        assertNull(sut.hotConfiguration()["usedAutosensOnMainPhone"])
    }

    @Test
    fun applyColdPutsWhetherAutosensIsUsedOnTheMaster() {
        sut.applyCold(NSRunningConfiguration(usedAutosensOnMainPhone = false))
        verify(preferences).put(eq(BooleanNonKey.AutosensUsedOnMainPhone), eq(false))
    }

    /** The hot doc applies a present scene snapshot. */
    @Test
    fun applyHotWithActiveSceneAppliesSnapshot() {
        val scene = NSActiveScene(sceneId = "scene-1", activatedAt = 1_000L, durationMs = 60_000L, lifecycle = "ACTIVE")
        sut.applyHot(NSRunningConfiguration(activeScene = scene))
        verify(activeSceneSync).applyActiveScene(
            eq(ActiveSceneSnapshot(sceneId = "scene-1", activatedAt = 1_000L, durationMs = 60_000L))
        )
    }

    /**
     * The skipped follow-up is said on the banner and nowhere else, so a client that does not carry the
     * reason over would show an ended scene with no word about the scene that never started.
     */
    @Test
    fun theReasonAFollowUpDidNotStartTravelsToTheClient() {
        whenever(activeSceneSync.activeSceneSnapshot()).thenReturn(
            ActiveSceneSnapshot(sceneId = "scene-1", activatedAt = 1_000L, durationMs = 60_000L, chainSkipReason = "the next scene is disabled")
        )

        val wire = sut.hotConfiguration()["activeScene"]?.jsonObject
        assertEquals(JsonPrimitive("the next scene is disabled"), wire?.get("chainSkipReason"))

        sut.applyHot(NSRunningConfiguration(activeScene = NSActiveScene(sceneId = "scene-1", activatedAt = 1_000L, durationMs = 60_000L, chainSkipReason = "the next scene is disabled")))
        verify(activeSceneSync).applyActiveScene(
            eq(ActiveSceneSnapshot(sceneId = "scene-1", activatedAt = 1_000L, durationMs = 60_000L, chainSkipReason = "the next scene is disabled"))
        )
    }

    /**
     * A String cold-channel key carried in the cold doc's `syncedPrefs` (e.g. AutomationEvents, which
     * moved onto the generic channel) must be adopted via `putRemote` — master-wins, version floored to
     * 0, no client→master echo — not a plain `put`. Guards the downlink apply path at the config layer.
     */
    @Test
    fun applyColdSyncedPrefsAdoptsStringColdKeyViaPutRemote() {
        val json = """[{"id":"x","title":"from-master"}]"""
        whenever(preferences.get(StringNonKey.AutomationEvents.key)).thenReturn(StringNonKey.AutomationEvents)
        sut.applyCold(NSRunningConfiguration(syncedPrefs = mapOf(StringNonKey.AutomationEvents.key to json)))
        verify(preferences).putRemote(StringNonKey.AutomationEvents, json, 0L)
    }

    /**
     * Plugin settings ride ONLY the generic cold `syncedPrefs` block now (flagged `SyncSpec(Cold, Bidirectional)`
     * — the legacy `apsConfiguration`/`sensitivityConfiguration`/`safetyConfiguration` sub-docs were deleted).
     * Representative guard that a plugin-settings key is flagged for the cold path, and that — carried in
     * `syncedPrefs` — it is adopted on the client via `putRemote` (master-wins, version floored).
     */
    @Test
    fun applyColdSyncedPrefsAdoptsPluginSettingDoubleViaPutRemote() {
        assertEquals(SyncChannel.Cold, DoubleKey.AutosensMin.sync?.channel)
        assertEquals(SyncDirection.Bidirectional, DoubleKey.AutosensMin.sync?.direction)
        whenever(preferences.get(DoubleKey.AutosensMin.key)).thenReturn(DoubleKey.AutosensMin)
        sut.applyCold(NSRunningConfiguration(syncedPrefs = mapOf(DoubleKey.AutosensMin.key to "0.85")))
        verify(preferences).putRemote(DoubleKey.AutosensMin, 0.85, 0L)
    }

    /**
     * The master's computed `isFakingTempsByExtendedBoluses` rides the cold doc; the client mirrors it READ-ONLY onto
     * its VirtualPump.fakeDataDetected. A `true` propagates.
     */
    @Test
    fun applyColdMirrorsFakingTrueOntoVirtualPump() {
        val pump = mock<Pump>(extraInterfaces = arrayOf(VirtualPump::class))
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        sut.applyCold(NSRunningConfiguration(isFakingTempsByExtendedBoluses = true))
        verify(pump as VirtualPump).fakeDataDetected = true
    }

    /** Mirror is verbatim, not one-way: `false` propagates too (the master turning the mode off reaches the client). */
    @Test
    fun applyColdMirrorsFakingFalseOntoVirtualPump() {
        val pump = mock<Pump>(extraInterfaces = arrayOf(VirtualPump::class))
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        sut.applyCold(NSRunningConfiguration(isFakingTempsByExtendedBoluses = false))
        verify(pump as VirtualPump).fakeDataDetected = false
    }

    /** Older master omits the field (null) → the client's flag is left untouched (no write). */
    @Test
    fun applyColdNullFakingLeavesVirtualPumpUntouched() {
        val pump = mock<Pump>(extraInterfaces = arrayOf(VirtualPump::class))
        whenever(activePlugin.activePumpInternal).thenReturn(pump)
        sut.applyCold(NSRunningConfiguration())
        verify(pump as VirtualPump, never()).fakeDataDetected = any()
    }

    /**
     * The pump type of the master goes into the driver's own description. Through the concentration wrapper
     * it would go into a copy when the insulin is not U100, and be lost.
     */
    @Test
    fun applyColdPutsThePumpTypeOfTheMasterIntoTheDriversOwnDescription() {
        val description = PumpDescription()
        val driver = mock<Pump>()
        whenever(driver.pumpDescription).thenReturn(description)
        whenever(activePlugin.activePumpInternal).thenReturn(driver)
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Generic AAPS")

        sut.applyCold(NSRunningConfiguration(pump = "Accu-Chek Combo"))

        assertEquals(PumpType.ACCU_CHEK_COMBO, description.pumpType)
        verify(activePlugin, never()).activePump
    }

    /**
     * Pre-init guard: before init completes no pump is selected, and activePump.isInitialized() would throw
     * "No pump selected" (PluginStore.activePumpInternal). configuration() must short-circuit to an empty doc
     * without touching the pump — RunningConfigurationPublisher then skips the empty payload and retries.
     */
    @Test
    fun configurationBeforeAppInitializedReturnsEmptyWithoutTouchingPump() {
        whenever(config.appInitialized).thenReturn(false)
        val result = sut.configuration()
        assertEquals(0, result.size)
        verify(activePlugin, never()).activePump
    }
}
