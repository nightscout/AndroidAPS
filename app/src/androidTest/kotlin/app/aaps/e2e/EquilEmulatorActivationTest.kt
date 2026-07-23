package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.aaps.core.data.plugin.PluginType
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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.ble.ScannedDevice
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
import app.aaps.pump.equil.compose.EquilWizardStep
import app.aaps.pump.equil.compose.EquilWizardViewModel
import app.aaps.pump.equil.compose.EquilWorkflow
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.emulator.EquilEmulatorBleTransport
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.manager.EquilManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
     * Runs the whole PAIR activation and asserts the pod ends `COMPLETED`.
     *
     * Each `startXxx` launches a `viewModelScope` coroutine that submits its command(s) through the
     * command queue and advances the wizard state on success; the test awaits the resulting state
     * transition before driving the next step. The final `CmdSettingSet` in [startConfirm] is a
     * pair-step — the command whose `executeCmd` wait can lose its wakeup against the fast emulator —
     * so the completion timeout is generous; increment work will tighten it once the race is fixed.
     *
     * One `@Test` on purpose: each activation drives ~13 separate queued commands and is not cheap, so
     * a second method would double the setup (and the pump plugin's foreground-service churn) for no
     * extra coverage. The transport-selection check folds in as the first assertion.
     */
    @Test
    fun pairWorkflow_activatesThePod() {
        bringUp()
        val start = SystemClock.uptimeMillis()

        assertThat(bleTransport).isInstanceOf(EquilEmulatorBleTransport::class.java)
        assertThat(equilManager.isActivationCompleted()).isFalse()

        // initializeWorkflow resolves the (profile-gated) page list asynchronously, then moves to the
        // first step. With an active profile that is ASSEMBLE.
        onMain { viewModel.initializeWorkflow(EquilWorkflow.PAIR) }
        awaitStep(EquilWizardStep.ASSEMBLE)

        // ASSEMBLE and ATTACH are instruction-only screens with no command.
        onMain { viewModel.moveToNextStep(EquilWizardStep.ASSEMBLE) }
        awaitStep(EquilWizardStep.BLE_SCAN)

        // Scan: the emulator emits its (randomly-serialled) device immediately; select the first one.
        onMain { viewModel.prepareBLEScan() }
        onMain { viewModel.startDeviceScan() }
        assertThat(awaitTrue(STEP_TIMEOUT) { viewModel.scannedDevices.value.isNotEmpty() }).isTrue()
        val device: ScannedDevice = viewModel.scannedDevices.value.first()
        onMain { viewModel.onDeviceSelected(device) }
        awaitStep(EquilWizardStep.PASSWORD)

        // Pairing: CmdDevicesOldGet (version) → CmdPair (key exchange) → CmdSettingSet (post-pair
        // limits) → FILL. First pass through the real EquilManager/EquilBLE handshake.
        onMain { viewModel.startPairing(PAIR_PASSWORD) }
        awaitStep(EquilWizardStep.FILL, PAIR_TIMEOUT)

        // Fill: CmdStepSet + CmdResistanceGet; the emulator's default resistance (>=500) reports the
        // piston reached on the first pass, so fillComplete flips after one iteration.
        onMain { viewModel.startFill() }
        assertThat(awaitTrue(PAIR_TIMEOUT) { viewModel.fillComplete.value }).isTrue()
        onMain { viewModel.finishFill() }
        awaitStep(EquilWizardStep.ATTACH)

        onMain { viewModel.moveToNextStep(EquilWizardStep.ATTACH) }
        awaitStep(EquilWizardStep.AIR)

        // Air removal: CmdStepSet(AIR), then finishAirStep() runs the PAIR tail —
        // CmdAlarmSet → CmdBasalSet → CmdTimeSet → CmdDevicesGet → CONFIRM.
        onMain { viewModel.startAirRemoval() }
        assertThat(awaitTrue(PAIR_TIMEOUT) { viewModel.airRemovalDone.value }).isTrue()
        onMain { viewModel.finishAirStep() }
        awaitStep(EquilWizardStep.CONFIRM, PAIR_TIMEOUT)

        // Confirm: CmdInsulinGet → CmdModelSet(RUN) → final CmdSettingSet → saveActivation → COMPLETED.
        onMain { viewModel.startConfirm() }
        val activated = awaitTrue(ACTIVATION_TIMEOUT) { equilManager.isActivationCompleted() }

        val elapsed = SystemClock.uptimeMillis() - start
        android.util.Log.i(TAG, "activation finished=$activated in ${elapsed}ms, progress=${equilManager.getActivationProgress()}")
        assertThat(activated).isTrue()
        assertThat(equilManager.getActivationProgress()).isEqualTo(ActivationProgress.COMPLETED)
    }

    // ---- helpers --------------------------------------------------------------------------------

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
    }
}
