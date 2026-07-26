package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import app.aaps.ComposeMainActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.pump.ble.PairingState
import app.aaps.core.interfaces.pump.ble.PairingStep
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.testcategories.ShardB
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Drives the **Dana-i pairing wizard UI** (`DanaRSPairWizardScreen`), which was at 0% coverage
 * because [DanaRsEmulatorUiTest] seeds an already-paired pump and so never reaches it.
 *
 * This seeds an **un**paired pump, so the overview offers "Pairing" instead of "Unpair", and walks
 * the wizard: the BLE-scan step really scans and lists the emulated device, then each subsequent
 * step screen is rendered by driving the transport's `pairingState` through
 * `EmulatorBleTransport.updatePairingState`.
 *
 * ## Why the pairing states are injected rather than handshaked
 * The wizard's step transitions come from `BLEComm` calling `updatePairingState` during a real
 * handshake. The emulator does not implement the BLE5 key exchange or the v3 PIN negotiation (it
 * uses a pre-shared key), so a first-time pairing cannot complete against it. What this test owns is
 * the **screen** — that each `WizardStep` renders the right content — while the state-machine logic
 * that maps a `PairingStep` to a `WizardStep` is covered directly by `DanaRSPairWizardViewModelTest`.
 * Injecting onto the same `pairingState` the wizard collects is the seam between the two.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardB
class DanaRSPairWizardUiTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var danaRSPlugin: app.aaps.pump.danars.DanaRSPlugin
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var insulin: Insulin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    private lateinit var emulator: EmulatorBleTransport

    @Before
    fun setUp() {
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_BLE5)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        seedConfiguredApp()
        // Deliberately NOT seeding RsName/MacAddress: an unconfigured pump is what makes the overview
        // offer the "Pairing" action (DanaOverviewViewModel.buildManagementActions).
        seedDanaAsActivePump()
        // Pin the name the emulator's scanner emits; otherwise deviceNameProvider generates a random
        // "UHH#####TI" on first scan and the assertion below can't know it up front.
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedLocalProfile()
        activateSeededProfile()

        emulator = bleTransport as EmulatorBleTransport
        emulator.pairingDelayMs = 0

        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
    }

    @After
    fun tearDown() {
        runCatching { emulator.updatePairingState(PairingState(step = PairingStep.IDLE)) }
        // configBuilder.initialize() + the activity launch enable and bind DanaRSPlugin's service.
        // Without this cleanup it stays bound and its queue work runs into the next test, which then
        // fails to connect its own pump (seen as pumpCommands/danaPumpUi failing right after this).
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { danaRSPlugin.disconnect("test end") }
        runCatching { danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        EmulatedOptions.enabled = emptySet()
        clearAllSharedPrefs()
    }

    @Test
    fun danaPairWizard_rendersEveryStep() {
        assertThat(activePlugin.activePumpInternal.isConfigured()).isFalse()

        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openVia("Manage", expect = PUMP_MANAGEMENT)
            openVia(PUMP_MANAGEMENT, expect = "Pairing")   // unpaired → the overview offers Pairing

            openVia("Pairing", expect = DEVICE_NAME)       // wizard opens, auto-scans, lists the pump
            assertVisibleContains("Select your pump")      // BleScanStep header

            // Each subsequent step is rendered by driving the pairing state the wizard collects.
            drivePairing(PairingStep.CONNECTING)
            assertVisibleContains("Connecting to pump")    // PairingProgressStep

            drivePairing(PairingStep.WAITING_FOR_PIN)
            assertVisibleContains("12 digits")             // EnterPinStep (num1pin field label)

            drivePairing(PairingStep.ERROR, message = ERROR_TEXT)
            assertVisibleContains(ERROR_TEXT)              // PairingErrorStep shows the message

            drivePairing(PairingStep.CONNECTED)
            assertVisibleContains("Pairing successful")    // PairingCompleteStep

            click("Done")                                  // finishWizard + back to the overview
            if (!waitForVisible("Pairing")) error("Did not return to the overview after Done")
        } catch (t: Throwable) {
            logScreen("E2E_PAIRWIZARD")
            throw t
        } finally {
            scenario.close()
        }
    }

    /** Emits [step] onto the transport's pairing flow and waits for the recomposition to settle. */
    private fun drivePairing(step: PairingStep, message: String? = null) {
        emulator.updatePairingState(PairingState(step = step, errorMessage = message))
        device.waitForIdle(IDLE_MS)
    }

    // ---- seeding (mirrors DanaRsEmulatorUiTest) -------------------------------------------------

    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
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
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = 100,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulin.iCfg
            )
        }
        checkNotNull(switch) { "Could not activate the seeded local profile" }
    }

    private fun seedDanaAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRSPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    private fun singleValue(value: Double) = """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

    // ---- ui helpers (same contract as DanaRsEmulatorUiTest) -------------------------------------

    private fun byText(s: String): BySelector = By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byDesc(s: String): BySelector = By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byTextContains(s: String): BySelector =
        By.text(Pattern.compile(".*" + Pattern.quote(s) + ".*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL))

    private fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    private fun click(label: String) = withStaleRetry { find(label).click() }

    private fun openVia(open: String, expect: String, attempts: Int = 4) {
        repeat(attempts) {
            click(open)
            if (waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on '$open'")
    }

    private fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    private fun assertVisibleContains(substring: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byTextContains(substring)) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for text containing '$substring'")
    }

    private fun waitForOverview() {
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            runCatching { device.findObject(byDesc("Close sheet"))?.click() }
            if (device.findObject(byDesc("Open navigation")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Overview never appeared within ${INIT_TIMEOUT}ms")
    }

    private inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block(); return
            } catch (e: StaleObjectException) {
                last = e; device.waitForIdle(STALE_SETTLE_MS)
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted")
    }

    private fun logScreen(tag: String) {
        runCatching {
            val items = device.findObjects(By.pkg(PKG)).mapNotNull { o ->
                val txt = runCatching { o.text }.getOrNull()?.takeIf { it.isNotBlank() }
                val desc = runCatching { o.contentDescription }.getOrNull()?.takeIf { it.isNotBlank() }
                if (txt != null || desc != null) "[t=$txt|d=$desc]" else null
            }
            items.joinToString(" ").chunked(3500).forEachIndexed { i, c -> android.util.Log.e(tag, "$i $c") }
        }
    }

    private fun <T> awaitNotNull(timeoutMs: Long, supplier: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            runCatching(supplier).getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val PUMP_MANAGEMENT = "Pump"
        private const val PROFILE_NAME = "LocalProfile1"

        /** The emulator's default scanned-device name; a Dana serial-number shape the wizard accepts. */
        private const val DEVICE_NAME = "UHH00002TI"
        private const val ERROR_TEXT = "PAIRING_TEST_ERROR"

        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 30_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val POLL_MS = 250L
    }
}
