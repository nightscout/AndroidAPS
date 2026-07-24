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
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.keys.DanaIntNonKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.testcategories.ShardB
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
 * Drives the **DanaR pairing wizard UI** (`DanaRPairWizardScreen`) and the pump overview that hosts it
 * (`DanaRComposeContent`), both at 0% coverage because [DanaREmulatorUiTest] seeds an already-paired
 * pump (via a programmatic status read) and so never opens the pairing screen.
 *
 * The RFCOMM counterpart to [DanaRSPairWizardUiTest] (which *injects* BLE pairing states the emulator
 * can't handshake). Here the flow is driven for real: the CONFIGURE step lists the emulator's bonded
 * device (`EmulatorRfcommTransport.getBondedDevices`), and tapping "Pairing" runs
 * `DanaRPairWizardViewModel.pair()` → `readStatus` → the CONNECTING step. That covers the WizardScreen
 * scaffold, ConfigureStep and ConnectingStep plus the view-model's `refreshBondedDevices` /
 * `onDeviceSelected` / `pair`.
 *
 * The pump is seeded UN-configured (no `RName`) so the overview offers "Pairing" (not "Unpair"); the
 * seeded `Password` (1234) matches `DanaRPumpState`'s default. The wizard only fires **one** `readStatus`
 * (unlike [DanaREmulatorUiTest], which loops it until initialized), and that single shot does not
 * reliably reach `isPasswordOK` on the loaded CI emulator, so this stops at CONNECTING rather than
 * flaking on the COMPLETE step. Covering CompleteStep/ErrorStep is left to a follow-up (e.g. a
 * view-model unit test that can drive `EventDanaRNewStatus` directly).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardB
class DanaRPairWizardUiTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky UI timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var danaRv2Plugin: DanaRv2Plugin
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
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_R_V2)
        hiltRule.inject()

        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS") }

        seedConfiguredApp()
        seedDanaAsActivePump()
        // Advertise the emulator under this name so the CONFIGURE step lists it (DAN#####EM matches the
        // wizard's Dana-name pattern). Seed the matching password so the real handshake verifies. NOT
        // RName: an unconfigured pump is what makes the overview offer "Pairing".
        preferences.put(DanaStringNonKey.EmulatorDeviceName, DEVICE_NAME)
        preferences.put(DanaIntNonKey.Password, EMULATOR_PASSWORD)

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
        runCatching { (danaRv2Plugin as Pump).disconnect("test end") }
        runCatching { danaRv2Plugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        EmulatedOptions.enabled = emptySet()
        clearAllSharedPrefs()
    }

    @Test
    fun danaRPairWizard_configuresAndStartsConnecting() {
        assertThat(activePlugin.activePumpInternal.isConfigured()).isFalse()

        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openVia("Manage", expect = PUMP_MANAGEMENT)
            openVia(PUMP_MANAGEMENT, expect = "Pairing")     // unpaired → the overview offers Pairing
            openVia("Pairing", expect = DEVICE_NAME)         // wizard opens, CONFIGURE lists the pump
            assertVisibleContains("Enter pump password")     // ConfigureStep header

            click(DEVICE_NAME)                               // select the bonded device (onDeviceSelected)
            clickLowest("Pairing")                           // primary button (not the toolbar title) → pair()

            assertVisibleContains("Connecting and verifying") // ConnectingStep — pair() ran and rendered
        } catch (t: Throwable) {
            logScreen("E2E_DANAR_PAIRWIZARD")
            throw t
        } finally {
            scenario.close()
        }
    }

    // ---- seeding (mirrors DanaRSPairWizardUiTest) -----------------------------------------------

    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
    }

    private fun seedDanaAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRv2Plugin", value = true)
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
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    private fun click(label: String) = withStaleRetry { find(label).click() }

    /**
     * Clicks the **lowest** on-screen node with [label]. The pairing wizard's primary button and the
     * toolbar title share the text "Pairing"; the button sits at the bottom, the title at the top, so
     * the lowest match is the button.
     */
    private fun clickLowest(label: String, timeout: Long = STEP_TIMEOUT) = withStaleRetry {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObjects(byText(label)).maxByOrNull { it.visibleBounds.centerY() }
                ?.let { it.click(); return@withStaleRetry }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

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

        /** Shaped like the name DanaModules generates for an emulated DanaR ("DAN#####EM"); matches the
         *  wizard's Dana-name pattern so the CONFIGURE step lists it. */
        private const val DEVICE_NAME = "DAN00001EM"
        /** Matches DanaRPumpState's default password so the real handshake verifies. */
        private const val EMULATOR_PASSWORD = 1234

        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 30_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val POLL_MS = 250L
    }
}
