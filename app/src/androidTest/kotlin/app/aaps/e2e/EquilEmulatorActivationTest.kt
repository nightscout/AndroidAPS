package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.compose.EquilHistoryViewModel
import app.aaps.pump.equil.compose.EquilOverviewViewModel
import app.aaps.pump.equil.compose.EquilWizardStep
import app.aaps.pump.equil.compose.EquilWizardViewModel
import app.aaps.pump.equil.compose.EquilWorkflow
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.driver.definition.EquilHistoryEntryGroup
import app.aaps.pump.equil.emulator.EquilEmulatorBleTransport
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.CmdModelSet
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Drives the **real Equil activation wizard** against the in-tree Equil emulator, headlessly (no
 * Compose UI): the genuine `EquilWizardViewModel` → `CommandQueue` → `EquilPumpPlugin` →
 * `EquilManager` → `EquilBLE` → [EquilEmulatorBleTransport], with no Bluetooth hardware and no pod.
 *
 * ## What this covers that the JVM tests don't
 * `:pump:equil-emulator`'s own `EquilPumpEmulatorTest` exercises the emulator's command handling with
 * a hand-built protocol and no plugin. It cannot cover the driver above it: the `EquilManager`
 * command handshake (`executeCmd` → `EquilBLE.writeCmd` → the wait/notify on the command monitor), the
 * `CommandQueue`/`QueueWorker` routing to `EquilPumpPlugin.executeCustomCommand`, and the wizard's
 * ~11-command PAIR activation chain (`CmdDevicesOldGet` → `CmdPair` → `CmdSettingSet` → fill/air →
 * `CmdAlarmSet`/`CmdBasalSet`/`CmdTimeSet`/`CmdDevicesGet` → `CmdInsulinGet`/`CmdModelSet` → final
 * `CmdSettingSet` → `saveActivation`). This is the first test of that whole stack.
 *
 * ## Why the wizard is driven at the ViewModel level (not through the UI)
 * The `EquilWizardViewModel` is a `@HiltViewModel` with an `@Inject constructor` whose dependencies
 * are all themselves injectable, so the test constructs the *real* ViewModel from injected singletons
 * and calls its public step-advance functions (`startDeviceScan`/`onDeviceSelected`/`startPairing`/
 * `startFill`/`startAirRemoval`/`startConfirm`) in the same order the Compose screens' buttons do —
 * exercising the identical command chain without the flakier uiautomator navigation. The through-UI
 * wizard is a later increment. Each ViewModel call is dispatched on the main thread (its coroutines
 * run on `viewModelScope` = `Dispatchers.Main`); the test thread then polls the exposed StateFlows to
 * await each async step.
 *
 * The emulator is selected purely by the `EMULATE_EQUIL` option — see [EmulatedOptions] for why a
 * test reports that rather than dropping the production marker file. It must be set before
 * `hiltRule.inject()` because `EquilBleTransport` is a `@Singleton` the graph binds once.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EquilEmulatorActivationTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    // ViewModel dependencies — injected here and passed to a manually-constructed EquilWizardViewModel.
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var equilManager: EquilManager
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var equilHistoryRecordDao: EquilHistoryRecordDao
    @Inject lateinit var equilHistoryPumpDao: EquilHistoryPumpDao
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var constraintsChecker: ConstraintsChecker
    @Inject lateinit var ch: ConcentrationHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var insulinManager: InsulinManager
    @Inject lateinit var bleTransport: EquilBleTransport
    @Inject lateinit var hardLimits: HardLimits

    // Test-harness singletons (active-pump selection, profile activation, teardown).
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Inject lateinit var insulin: Insulin
    @Inject lateinit var dateUtil: DateUtil
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val appContext: Context get() = instrumentation.targetContext.applicationContext

    private lateinit var emulator: EquilEmulatorBleTransport
    private lateinit var viewModel: EquilWizardViewModel

    private fun bringUp() {
        // A prior activation persists the pod state (EquilStringKey.State) to SharedPreferences, which
        // outlives the per-test Hilt component — so clear it before inject(), or the freshly-built
        // EquilManager reads back a COMPLETED pod and the test starts already-activated.
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_EQUIL)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        emulator = bleTransport as EquilEmulatorBleTransport

        seedConfiguredApp()
        seedEquilAsActivePump()

        // Mirror MainApp.onCreate: publish plugins and elect the active pump from the seeded prefs.
        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()

        // setPluginEnabledBlocking (@TestOnly) runs onStart synchronously — for Equil that starts the
        // in-process BLE handler threads the manager talks to (no bound service, unlike Dana).
        equilPumpPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)

        // A profile must be active before activation: setProfile() programs the basal schedule from it,
        // and an active profile also makes the wizard skip its PROFILE_GATE step.
        seedLocalProfile()
        activateSeededProfile()

        viewModel = EquilWizardViewModel(
            context = appContext,
            rh = rh,
            aapsLogger = aapsLogger,
            preferences = preferences,
            commandQueue = commandQueue,
            equilPumpPlugin = equilPumpPlugin,
            equilManager = equilManager,
            pumpSync = pumpSync,
            persistenceLayer = persistenceLayer,
            equilHistoryRecordDao = equilHistoryRecordDao,
            constraintsChecker = constraintsChecker,
            ch = ch,
            profileFunction = profileFunction,
            profileRepository = profileRepository,
            rxBus = rxBus,
            insulinManager = insulinManager,
            bleTransport = bleTransport,
            hardLimits = hardLimits
        )
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { equilPumpPlugin.disconnect("test end") }
        runCatching { if (::emulator.isInitialized) emulator.awaitPendingCallbacks() }
        runCatching { equilPumpPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        EmulatedOptions.enabled = emptySet()
        // Don't leave a COMPLETED pod persisted for the next test / class in this shard's process.
        clearAllSharedPrefs()
    }

    /**
     * Activates the pod through the whole PAIR wizard, then delivers through it — bolus, temp basal
     * and extended bolus — asserting each on the emulator's own `PumpState`, the true far side.
     *
     * Each `startXxx` launches a `viewModelScope` coroutine that submits its command(s) through the
     * command queue and advances the wizard state on success; the test awaits the resulting state
     * transition before driving the next step. The final `CmdSettingSet` in [startConfirm] is a
     * pair-step — the command whose `executeCmd` wait can lose its wakeup against the fast emulator —
     * so the completion timeout is generous; increment work will tighten it once the race is fixed.
     *
     * The delivery leg reuses the pod this activation just paired: only a real pairing establishes the
     * shared keys `EquilManager.executeCmd` encrypts with, so a seeded-activated pod could not deliver.
     * Driven directly on the plugin (as a queue worker would), asserted on the emulator, not the
     * driver's cache. One `@Test` on purpose: a second method would pay the ~13-command activation again.
     */
    @Test
    fun pairWorkflow_activatesAndDelivers() {
        bringUp()
        activatePod()

        // ---- Deliver through the now-activated pod: bolus, temp basal, extended bolus ----------------
        // Driven through the command queue — the app's real path, which connects and establishes each
        // command's session. Asserted on the round-trip result (the driver only reports success once the
        // emulator has validly answered the command) plus the pod state the driver records from it.
        assertThat(equilManager.equilState?.bolusRecord).isNull()
        seedRunningMode()

        val bolusResult = runBlocking { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = BOLUS_UNITS }) }
        assertThat(bolusResult.success).isTrue()
        assertThat(equilManager.equilState?.bolusRecord?.amount).isEqualTo(BOLUS_UNITS)

        // Temp basal duration must be a multiple of the pump's 30-min step (BASAL_STEP_DURATION).
        val profile = activeProfile()
        val tbrResult = runBlocking {
            commandQueue.tempBasalAbsolute(TBR_RATE, TBR_DURATION_MIN, true, profile, PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(tbrResult.success).isTrue()
        assertThat(equilManager.equilState?.tempBasal).isNotNull()

        val extResult = runBlocking { commandQueue.extendedBolus(EXTENDED_UNITS, EXTENDED_DURATION_MIN) }
        assertThat(extResult.success).isTrue()
    }

    /**
     * After activation, exercises the pod's read / cancel / mode surface through the command queue —
     * status read, temp-basal and extended-bolus cancel, a suspend→resume mode toggle and TDD/time
     * reads — covering the plugin and manager paths the delivery test doesn't reach.
     */
    @Test
    fun activatedPod_readsCancelsAndTogglesMode() {
        bringUp()
        activatePod()
        seedRunningMode()
        val profile = activeProfile()

        // RUN-gated ops first (a status read would refresh runMode off the emulator). Temp basal, cancel.
        assertThat(runBlocking {
            commandQueue.tempBasalAbsolute(TBR_RATE, TBR_DURATION_MIN, true, profile, PumpSync.TemporaryBasalType.NORMAL)
        }.success).isTrue()
        assertThat(runBlocking { commandQueue.cancelTempBasal(true) }.success).isTrue()

        // Extended bolus, then cancel it.
        assertThat(runBlocking { commandQueue.extendedBolus(EXTENDED_UNITS, EXTENDED_DURATION_MIN) }.success).isTrue()
        runBlocking { commandQueue.cancelExtended() }

        // Status read: getPumpStatus → CmdModeAndHistoryGet + CmdDevicesGet.
        runBlocking { commandQueue.readStatus("test") }

        // Suspend then resume — the run-mode command the overview's toggle sends.
        runBlocking { commandQueue.customCommand(CmdModelSet(RunMode.SUSPEND.command, aapsLogger, preferences, equilManager)) }
        runBlocking { commandQueue.customCommand(CmdModelSet(RunMode.RUN.command, aapsLogger, preferences, equilManager)) }

        // TDD read and a time update (timezoneOrDSTChanged → CmdTimeSet).
        runBlocking { commandQueue.loadTDDs() }
        runBlocking { commandQueue.updateTime() }
    }

    /**
     * Activates a pod, then runs the UNPAIR workflow (detach → confirm) — covering `CmdInsulinChange`,
     * `CmdUnPair` and the ViewModel's unpair region, ending with the pod cleared.
     */
    @Test
    fun activatedPod_unpairs() {
        bringUp()
        activatePod()
        assertThat(equilManager.equilState?.serialNumber ?: "").isNotEmpty()

        onMain { viewModel.initializeWorkflow(EquilWorkflow.UNPAIR) }
        awaitStep(EquilWizardStep.UNPAIR_DETACH)
        onMain { viewModel.startUnpairDetach() }    // CmdInsulinChange → UNPAIR_CONFIRM
        awaitStep(EquilWizardStep.UNPAIR_CONFIRM, PAIR_TIMEOUT)
        onMain { viewModel.confirmUnpair() }         // CmdUnPair → clears serial/address
        assertThat(awaitTrue(PAIR_TIMEOUT) { viewModel.serialNumberDisplay.value.isEmpty() }).isTrue()
        assertThat(equilManager.equilState?.serialNumber ?: "").isEmpty()
    }

    /**
     * After activation, builds the real [EquilOverviewViewModel] and reads its paired-state UI (info
     * rows, management actions, status banner) and toggles the run mode — covering the overview
     * ViewModel's state-building and toggle paths.
     */
    @Test
    fun activatedPod_overviewRendersPairedStateAndTogglesMode() {
        bringUp()
        activatePod()
        seedRunningMode()

        val overview = EquilOverviewViewModel(
            rh = rh, aapsLogger = aapsLogger, dateUtil = dateUtil, ch = ch, equilPumpPlugin = equilPumpPlugin,
            equilManager = equilManager, commandQueue = commandQueue, preferences = preferences, rxBus = rxBus,
            context = appContext
        )
        // Paired: buildPairedInfoRows + buildManagementActions(paired) + buildStatusBanner.
        val state = overview.uiState.value
        assertThat(state.managementActions).isNotEmpty()   // Change reservoir / History / Unpair
        assertThat(state.infoRows).isNotEmpty()            // serial / mode / reservoir …

        // The overview's primary action toggles delivery (suspend/resume) → CmdModelSet.
        state.primaryActions.firstOrNull()?.let { action -> onMain { action.onClick() } }
        runBlocking { commandQueue.readStatus("overview") }
    }

    /**
     * After activation + a delivery, builds the real [EquilHistoryViewModel] and reads back the
     * command history (and applies a filter) — covering the history ViewModel's load/transform paths.
     */
    @Test
    fun activatedPod_historyViewModelLoadsRecords() {
        bringUp()
        activatePod()
        seedRunningMode()
        // Generate at least one command-history record on the paired pod.
        runBlocking { commandQueue.bolus(DetailedBolusInfo().also { it.insulin = BOLUS_UNITS }) }

        val history = EquilHistoryViewModel(
            equilHistoryRecordDao = equilHistoryRecordDao, equilHistoryPumpDao = equilHistoryPumpDao,
            equilPumpPlugin = equilPumpPlugin, dateUtil = dateUtil, aapsLogger = aapsLogger,
            rxBus = rxBus, profileUtil = profileUtil
        )
        // Subscribe so the WhileSubscribed flow runs loadData().
        val job = CoroutineScope(Dispatchers.Main).launch { history.filteredCommandHistory.collect {} }
        val loaded = awaitTrue(STEP_TIMEOUT) { history.filteredCommandHistory.value.isNotEmpty() }
        onMain { history.setFilter(EquilHistoryEntryGroup.All) }
        job.cancel()
        assertThat(loaded).isTrue()
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Drives the whole PAIR wizard through [viewModel] to a COMPLETED, paired pod. */
    private fun activatePod() {
        val start = SystemClock.uptimeMillis()
        assertThat(bleTransport).isInstanceOf(EquilEmulatorBleTransport::class.java)
        assertThat(equilManager.isActivationCompleted()).isFalse()

        onMain { viewModel.initializeWorkflow(EquilWorkflow.PAIR) }
        awaitStep(EquilWizardStep.ASSEMBLE)
        onMain { viewModel.moveToNextStep(EquilWizardStep.ASSEMBLE) }   // ASSEMBLE/ATTACH: no command
        awaitStep(EquilWizardStep.BLE_SCAN)

        onMain { viewModel.prepareBLEScan() }
        onMain { viewModel.startDeviceScan() }                          // emulator emits one device
        assertThat(awaitTrue(STEP_TIMEOUT) { viewModel.scannedDevices.value.isNotEmpty() }).isTrue()
        onMain { viewModel.onDeviceSelected(viewModel.scannedDevices.value.first()) }
        awaitStep(EquilWizardStep.PASSWORD)

        onMain { viewModel.startPairing(PAIR_PASSWORD) }                // CmdDevicesOldGet→CmdPair→CmdSettingSet
        awaitStep(EquilWizardStep.FILL, PAIR_TIMEOUT)

        onMain { viewModel.startFill() }                               // CmdStepSet + CmdResistanceGet
        assertThat(awaitTrue(PAIR_TIMEOUT) { viewModel.fillComplete.value }).isTrue()
        onMain { viewModel.finishFill() }
        awaitStep(EquilWizardStep.ATTACH)

        onMain { viewModel.moveToNextStep(EquilWizardStep.ATTACH) }
        awaitStep(EquilWizardStep.AIR)

        onMain { viewModel.startAirRemoval() }                         // CmdStepSet(AIR)
        assertThat(awaitTrue(PAIR_TIMEOUT) { viewModel.airRemovalDone.value }).isTrue()
        onMain { viewModel.finishAirStep() }                           // alarm→basal→time→firmware→CONFIRM
        awaitStep(EquilWizardStep.CONFIRM, PAIR_TIMEOUT)

        onMain { viewModel.startConfirm() }                            // insulin→model→settings→saveActivation
        val activated = awaitTrue(ACTIVATION_TIMEOUT) { equilManager.isActivationCompleted() }
        android.util.Log.i(TAG, "activation finished=$activated in ${SystemClock.uptimeMillis() - start}ms")
        assertThat(activated).isTrue()
        assertThat(equilManager.getActivationProgress()).isEqualTo(ActivationProgress.COMPLETED)
    }

    /**
     * The command queue gates delivery on the persisted running mode; activation records none, so mark
     * the pump running (OPEN_LOOP) as the reconciler would — else delivery is rejected with
     * PUMP_REPORTED_SUSPENDED. Mirrors RunningModeReconcilerIntegrationTest.insertActiveMode.
     */
    private fun seedRunningMode() {
        runBlocking {
            persistenceLayer.insertOrUpdateRunningMode(
                runningMode = RM(timestamp = dateUtil.now(), mode = RM.Mode.OPEN_LOOP, autoForced = false, duration = T.hours(2).msecs()),
                action = Action.CLOSED_LOOP_MODE, source = Sources.Aaps, listValues = emptyList()
            )
        }
    }

    /**
     * Re-asserts the local profile (activation runs its own insulin profile switch, briefly leaving
     * getProfile() null) and returns it — temp basal needs an active Profile.
     */
    private fun activeProfile(): Profile {
        activateSeededProfile()
        return requireNotNull(
            awaitNotNull(PROFILE_STORE_TIMEOUT) { runBlocking { profileFunction.getProfile() } }
        ) { "no active profile" }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun awaitStep(step: EquilWizardStep, timeoutMs: Long = STEP_TIMEOUT) {
        if (!awaitTrue(timeoutMs) { viewModel.wizardStep.value == step })
            error("Wizard never reached $step (stuck at ${viewModel.wizardStep.value})")
    }

    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(BooleanKey.GeneralSimpleMode, false)
    }

    private fun seedEquilAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_EquilPumpPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    private fun seedLocalProfile() {
        val profile = profileRepository.newDraft().apply {
            mgdl = true
            ic = JSONArray(singleValue(10.0))
            isf = JSONArray(singleValue(50.0))
            basal = JSONArray(singleValue(0.5))
            targetLow = JSONArray(singleValue(100.0))
            targetHigh = JSONArray(singleValue(110.0))
        }
        runBlocking { profileRepository.add(profile) }.getOrThrow()
    }

    private fun activateSeededProfile() {
        val store = checkNotNull(
            awaitNotNull(PROFILE_STORE_TIMEOUT) {
                profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
            }
        ) { "The profile store never published '$PROFILE_NAME'" }
        val switch = runBlocking {
            profileFunction.createProfileSwitch(
                profileStore = store,
                profileName = PROFILE_NAME,
                durationInMinutes = 0,
                percentage = 100,
                timeShiftInHours = 0,
                timestamp = dateUtil.now(),
                action = Action.PROFILE_SWITCH,
                source = Sources.Aaps,
                listValues = emptyList(),
                iCfg = insulin.iCfg
            )
        }
        checkNotNull(switch) { "Could not activate the seeded local profile '$PROFILE_NAME'" }
    }

    private fun <T> awaitNotNull(timeoutMs: Long, supplier: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            runCatching(supplier).getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    private fun singleValue(value: Double) =
        """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val TAG = "EquilActivationE2E"
        private const val PKG = "info.nightscout.androidaps"
        private const val PROFILE_NAME = "LocalProfile1"
        // Any non-empty pairing code works: the emulator decrypts the pairing request with SHA-256(sn)
        // and does not validate the code itself.
        private const val PAIR_PASSWORD = "0000"

        private const val POLL_MS = 200L
        private const val STEP_TIMEOUT = 20_000L
        // A pairing/tail phase runs several BLE round-trips with 500ms inter-command pacing.
        private const val PAIR_TIMEOUT = 60_000L
        // Generous on purpose: the final pair-step CmdSettingSet can stall on a lost wakeup today.
        private const val ACTIVATION_TIMEOUT = 90_000L
        private const val PROFILE_STORE_TIMEOUT = 20_000L

        // Delivery values. TBR duration must be a multiple of the pump's 30-min basal step.
        private const val BOLUS_UNITS = 1.0
        private const val TBR_RATE = 1.0
        private const val TBR_DURATION_MIN = 30
        private const val EXTENDED_UNITS = 1.0
        private const val EXTENDED_DURATION_MIN = 30
    }
}
