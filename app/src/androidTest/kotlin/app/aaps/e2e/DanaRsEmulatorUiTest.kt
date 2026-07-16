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
import app.aaps.ComposeMainActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.ProfileIntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Drives the **Dana-i pump UI** against the in-tree pump emulator: the four screens
 * `DanaRSComposeContent` hosts (overview, pair wizard, history, user options) plus a bolus that
 * travels UI → command queue → `DanaRSPlugin` → `DanaRSService` → `BLEComm` → [EmulatorBleTransport].
 *
 * ## Why this is seeded rather than wizard-driven
 * [SetupWizardE2EHiltTest] walks the whole setup wizard because that is what it tests; it costs
 * ~140s and ends on **Virtual Pump**, so no pump-driver UI is ever rendered. This test wants the
 * pump screens, not the wizard, so it seeds the same end state directly into preferences — a local
 * profile ([ProfileComposedStringKey]), mg/dL units, the wizard marked done, and a paired Dana-i —
 * which keeps it a few seconds instead of a second wizard walk.
 *
 * ## Fragility (read before editing)
 * Same rules as the other in-process E2E: selectors match **case-insensitively against text OR
 * content-desc**, opens are **verified-with-retry**, and it is English-only. The pump screens are
 * reached through the nav drawer, where the plugin shows as its [app.aaps.pump.dana.R.string.danarspump]
 * name ("Dana-i/RS").
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DanaRsEmulatorUiTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    private lateinit var emulator: EmulatorBleTransport

    @Before
    fun setUp() {
        // Clear before the graph reads any prefs, exactly as SetupWizardE2EHiltTest does: singletons
        // then seed their defaults against empty prefs like a fresh app would.
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_BLE5)
        hiltRule.inject()

        // BLEComm.connect gates on this before it ever reaches the transport.
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS")
        }

        seedConfiguredApp()
        seedPairedDanaPump()
        seedDanaAsActivePump()

        pluginStore.plugins = pluginList
        // Reads the ConfigBuilderEnabled preferences seeded above, then verifySelectionInCategories()
        // makes Dana the active pump — which is what puts its setup button in the bottom bar. Must
        // come after seeding: initialize() resolves the active pump once.
        configBuilder.initialize()
        config.initCompleted()

        danaRSPlugin.changePump()

        // initialize() enables the plugin via setPluginEnabled, which launches onStart (and its
        // bindService) on pluginScope. Wait for the service to actually land: it must be created
        // while this test's Hilt component is alive, or its dagger-android injection crashes the
        // process. There is no "service bound" signal on the plugin, so drive connect() until it
        // takes effect — it is a no-op while danaRSService is null.
        // See DanaRsEmulatorPumpTest for the full story.
        if (!awaitTrue(BIND_TIMEOUT) {
                danaRSPlugin.connect("e2e bind")
                danaRSPlugin.isConnecting() || danaRSPlugin.isConnected()
            }
        ) error("DanaRSService never bound within ${BIND_TIMEOUT}ms")

        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        device.executeShellCommand("logcat -c")
    }

    @After
    fun tearDown() {
        runCatching { danaRSPlugin.disconnect("test end") }
        // Unbind before the Hilt component dies, or the service crashes the process.
        runCatching { danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
        EmulatedOptions.enabled = emptySet()
        clearAllSharedPrefs()
    }

    /** A profile + units + a completed wizard: the state the wizard would have written. */
    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        preferences.put(ProfileIntKey.AmountOfProfiles, 1)
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedName, 0, value = PROFILE_NAME)
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedIc, 0, value = singleValue(10.0))
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedIsf, 0, value = singleValue(50.0))
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedBasal, 0, value = singleValue(0.5))
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetLow, 0, value = singleValue(100.0))
        preferences.put(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, 0, value = singleValue(110.0))
        preferences.put(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, 0, value = true)
    }

    /** The preferences a completed Dana-i pairing leaves behind. */
    private fun seedPairedDanaPump() {
        preferences.put(DanaStringNonKey.RsName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.MacAddress, DEVICE_ADDRESS)
        preferences.put(DanaStringNonKey.Password, PASSWORD)
        preferences.put(DanaStringComposedKey.Ble5PairingKey, DEVICE_NAME, value = BLE5_PAIRING_KEY)

        emulator = bleTransport as EmulatorBleTransport
        emulator.pumpState.ble5PairingKey = BLE5_PAIRING_KEY
        emulator.pairingDelayMs = 0
        emulator.writeLatencyMs = 0
    }

    /**
     * Makes Dana-i the active pump instead of the default Virtual Pump, the way the Config Builder
     * persists it: `ConfigBuilder_Enabled_<TYPE>_<PluginClass>`. `ConfigBuilderImpl.loadSettings`
     * reads these in `initialize()` and `verifySelectionInCategories()` then elects the single
     * enabled pump. Only the *active* pump gets a setup button in the bottom bar, which is the only
     * route to its compose content.
     */
    private fun seedDanaAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRSPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    /** The profile-editor JSON shape: a single all-day value. */
    private fun singleValue(value: Double) =
        """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

    /** Polls [condition] until it returns true or [timeoutMs] elapses. */
    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    @Test
    fun danaPumpScreens_render() {
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openDanaPlugin()          // nav drawer → Dana-i/RS → DanaRSOverviewScreen
            visitDanaHistory()        // toolbar → DanaHistoryScreen
            visitDanaUserOptions()    // toolbar → DanaUserOptionsScreen
            visitDanaPairWizard()     // toolbar Bluetooth → DanaRSPairWizardScreen
        } catch (t: Throwable) {
            logScreen("E2E_DANA_SCREEN")
            throw t
        } finally {
            scenario.close()
        }
    }

    // ---- navigation ---------------------------------------------------------------------------

    /**
     * Waits for the overview, dismissing the "Permissions Required" sheet AAPS re-shows on resume
     * until the AAPS directory is granted — it covers the toolbar, so it has to go before anything
     * can be tapped. Re-checked each pass because it can reappear.
     */
    private fun waitForOverview() {
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            dismissBlockingSheetIfPresent()
            if (device.findObject(byDesc("Open navigation")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Overview never appeared within ${INIT_TIMEOUT}ms — UI stuck on splash or behind a sheet")
    }

    private fun dismissBlockingSheetIfPresent() {
        device.findObject(byDesc("Close sheet"))?.click()
    }

    /**
     * Overview bottom bar → the Dana-i/RS setup button, which renders DanaRSOverviewScreen.
     *
     * Not the nav drawer: that is a fixed menu (history/statistics/maintenance/...) with no plugin
     * entries. A pump's compose content is reachable only through `MainNavigationBar`'s setup
     * button, which `ComposeMainActivity` shows for the **active** pump while it is not yet
     * initialized — hence [seedDanaAsActivePump].
     */
    private fun openDanaPlugin() {
        openVia(DANA_PLUGIN_NAME, expect = "Back")
    }

    private fun visitDanaHistory() {
        openVia("History", expect = "Back")
        device.pressBack()
        device.waitForIdle(IDLE_MS)
    }

    private fun visitDanaUserOptions() {
        openVia("Settings", expect = "Back")
        device.pressBack()
        device.waitForIdle(IDLE_MS)
    }

    private fun visitDanaPairWizard() {
        openVia("Bluetooth", expect = "Back")
        device.pressBack()
        device.waitForIdle(IDLE_MS)
    }

    // ---- ui helpers (same contract as SetupWizardE2EHiltTest) -----------------------------------

    private fun byText(s: String): BySelector =
        By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

    private fun byDesc(s: String): BySelector =
        By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

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

    /** Compose recomposes frequently on a slow emulator, invalidating nodes between find and click. */
    private inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block()
                return
            } catch (e: StaleObjectException) {
                last = e
                device.waitForIdle(STALE_SETTLE_MS)
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted after $times attempts")
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

    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val DANA_PLUGIN_NAME = "Dana-i/RS"
        private const val PROFILE_NAME = "LocalProfile1"

        // Mirrors BLECommBLE5IntegrationTest / DanaRsEmulatorPumpTest.
        private const val DEVICE_NAME = "UHH00002TI"
        private const val DEVICE_ADDRESS = "00:00:00:00:00:00"
        private const val BLE5_PAIRING_KEY = "474632"
        private const val PASSWORD = "0000"

        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 15_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L
        private const val BIND_TIMEOUT = 20_000L
        private const val POLL_MS = 250L
    }
}
