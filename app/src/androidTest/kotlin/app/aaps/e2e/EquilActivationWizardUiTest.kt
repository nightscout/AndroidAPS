package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.work.WorkManager
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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.manager.EquilManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Drives the **Equil pod activation wizard through its real Compose UI** end to end, against the
 * in-tree Equil emulator: Manage → Pump → the overview's "Pair" button → the whole PAIR wizard
 * (ASSEMBLE → BLE scan → PASSWORD → FILL → ATTACH → AIR → CONFIRM), each screen's button clicked with
 * uiautomator, until the pod reports `COMPLETED` and the overview repaints as paired.
 *
 * Unlike `DanaRSPairWizardUiTest` — whose emulator can't complete a first-time handshake, so it
 * *injects* pairing states and only asserts each screen renders — the Equil emulator implements real
 * pre-shared-key pairing, so this walks the genuine ~13-command activation chain the buttons trigger.
 * `EquilEmulatorActivationTest` covers the same chain headlessly at the ViewModel level; this one adds
 * the actual screens and their button wiring on top.
 *
 * ## Reaching exactly the seven PAIR steps
 * `initializeWorkflow` inserts conditional steps: a PROFILE_GATE when no profile is active, a
 * SELECT_INSULIN when more than one insulin is configured, and a SITE_LOCATION when
 * `SiteRotationManagePump` is on. [setUp] seeds an active profile, leaves a single insulin, and turns
 * site rotation off, so none of the three appear and the path is the fixed seven above.
 *
 * ## Fragility (read before editing)
 * Selectors match case-insensitively against text OR content-desc; steps are gated on a **unique**
 * in-content string before the (often ambiguously-labelled, e.g. several "Next") button is clicked;
 * the FILL/AIR "Next" only enables after its async command settles, so [clickWhenEnabled] polls. It is
 * English-only. The activation chain is ~75s of real command round-trips, so the step timeouts are
 * generous.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EquilActivationWizardUiTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky UI timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var equilManager: EquilManager
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

    @Before
    fun setUp() {
        // A prior activation persists the pod state (EquilStringKey.State); clear before inject() or
        // the fresh EquilManager reads back a COMPLETED pod and the overview never offers "Pair".
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_EQUIL)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        seedConfiguredApp()
        seedEquilAsActivePump()

        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()
        seedLocalProfile()
        activateSeededProfile()

        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
    }

    @After
    fun tearDown() {
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        runCatching { equilPumpPlugin.disconnect("test end") }
        runCatching { equilPumpPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        EmulatedOptions.enabled = emptySet()
        clearAllSharedPrefs()
    }

    @Test
    fun equilActivationWizard_activatesThroughUi() {
        assertThat(equilManager.isActivationCompleted()).isFalse()

        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()

            // Manage → Pump → the Equil overview; unpaired, so it offers "Pair".
            openVia("Manage", expect = PUMP_MANAGEMENT)
            openVia(PUMP_MANAGEMENT, expect = "Pair")
            openVia("Pair", expect = "Pair Equil patch pump")   // overview "Pair" → ASSEMBLE header

            // Every wizard step transition retries its click until the next screen's unique text
            // appears (openVia): a single "Next" tap is routinely dropped during Compose recomposition,
            // and the plain moveStep transitions are idempotent so re-clicking is safe.
            openVia("Next", expect = "Select your pump")        // ASSEMBLE → BLE_SCAN

            // BLE_SCAN: the emulator advertises one device row "Equil - A#####".
            openVia("Equil -", expect = "Enter password for")   // tap device row (prefix) → PASSWORD

            // PASSWORD: empty code is accepted; "Pair" runs getVersion → pair → pumpSettings (a command
            // chain, so a single click — retrying would re-start pairing).
            click("Pair")
            assertVisibleContains("Please prime the reservoir", PAIR_TIMEOUT)  // FILL

            // FILL: "Prime/Fill" runs the fill loop; the emulator's resistance completes it in one pass,
            // then the primary relabels to "Next".
            click("Prime/Fill")
            waitForVisibleOrThrow("Next", FILL_TIMEOUT)
            openVia("Next", expect = "Please attach the pump")  // FILL → ATTACH

            // ATTACH → AIR
            openVia("Next", expect = "purge the air")           // AIR (equil_hint1 body)

            // AIR: "Purge air" runs the air command; once it settles the "Next" enables and runs the
            // alarm → basal → time → firmware tail into CONFIRM. Retry "Next" until CONFIRM appears —
            // each attempt's wait outlasts the tail chain, so it never double-fires finishAirStep.
            click("Purge air")
            openVia("Next", expect = "Implant the pump", attempts = 6)  // AIR → CONFIRM

            // CONFIRM: "Finish" runs insulin → model → final settings → saveActivation → COMPLETED,
            // then the wizard closes back to the (now paired) overview.
            openVia("Finish", expect = "Unpair device", attempts = 4)  // paired-state overview
        } catch (t: Throwable) {
            logScreen("E2E_EQUIL_WIZARD")
            throw t
        } finally {
            scenario.close()
        }

        assertThat(equilManager.isActivationCompleted()).isTrue()
    }

    // ---- seeding --------------------------------------------------------------------------------

    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(BooleanKey.GeneralSimpleMode, false)
        // Keep the wizard to the seven PAIR steps — no SITE_LOCATION.
        preferences.put(BooleanKey.SiteRotationManagePump, false)
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
                profileStore = store, profileName = PROFILE_NAME, durationInMinutes = 0, percentage = 100,
                timeShiftInHours = 0, timestamp = dateUtil.now(), action = Action.PROFILE_SWITCH,
                source = Sources.Aaps, listValues = emptyList(), iCfg = insulin.iCfg
            )
        }
        checkNotNull(switch) { "Could not activate the seeded local profile" }
    }

    private fun singleValue(value: Double) = """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

    // ---- ui helpers (same contract as DanaRSPairWizardUiTest) -----------------------------------

    private fun byText(s: String): BySelector = By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byDesc(s: String): BySelector = By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))
    private fun byTextContains(s: String): BySelector =
        By.text(Pattern.compile(".*" + Pattern.quote(s) + ".*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL))

    private fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.findObject(byTextContains(label))?.let { return it }
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
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null ||
                device.findObject(byTextContains(label)) != null
            ) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    private fun waitForVisibleOrThrow(label: String, timeout: Long = STEP_TIMEOUT) {
        if (!waitForVisible(label, timeout)) error("Timed out after ${timeout}ms looking for '$label'")
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

        // Generous: the activation UI launches the full ComposeMainActivity and then runs a long
        // command chain, so first paint can lag well past a lighter screen's under CI/host load.
        private const val INIT_TIMEOUT = 120_000L
        private const val STEP_TIMEOUT = 30_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val POLL_MS = 250L

        // Command-driven transitions: each spans several BLE round-trips with 500ms inter-command pacing.
        private const val PAIR_TIMEOUT = 60_000L
        private const val FILL_TIMEOUT = 30_000L
    }
}
