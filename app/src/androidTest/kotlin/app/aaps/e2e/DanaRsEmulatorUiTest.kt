package app.aaps.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.ComposeMainActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.pump.dana.emulator.ReviewRecordCodes
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import app.aaps.testcategories.ShardA
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import javax.inject.Inject

/**
 * Drives the **Dana-i UI** against the in-tree pump emulator, with no Bluetooth hardware and no
 * pump: `DanaRSOverviewScreen` / `DanaHistoryScreen` / `DanaUserOptionsScreen` and the insulin
 * dialog, each reaching the emulated pump through the whole production stack — UI → `CommandQueue` →
 * `DanaRSPlugin` → `DanaRSService` → `BLEComm` → [EmulatorBleTransport].
 *
 * Covers both directions, and asserts each on the far side rather than on the screen that caused it:
 * - **pump → UI**: a value seeded onto the emulator has to appear after Refresh
 * - **UI → pump**: a user option edited here, and a bolus, have to land on the emulator's `PumpState`
 *
 * The variant (which `EMULATE_DANA_*` handshake) is chosen per test in [bringUp], not `@Before`,
 * because `BleTransport` is a `@Singleton` the graph binds once — so the option has to be set before
 * `hiltRule.inject()`. The BLE5 test walks every screen; [danaInsulinDelivery_overV1Handshake] and
 * [danaInsulinDelivery_overV3Handshake] run only the insulin-delivery flow, which is pump-agnostic
 * (Manage → `CommandQueue` → active pump), so the *same* body proves each handshake — the reuse that
 * makes an E2E worth more than a per-plugin unit test. `DanaRsEmulatorPumpTest` covers the same
 * stack headlessly; this one adds the screens on top.
 *
 * The pump-agnostic machinery (seeding the configured app, the insulin-delivery flow, navigation and
 * uiautomator helpers) lives in [AbstractDanaEmulatorUiTest]; this class supplies only the Dana-i
 * (RS) wiring — the pairing seed, the emulator-state reads, and the RS-only screen legs.
 *
 * ## Why this is seeded rather than wizard-driven
 * `SetupWizardE2EHiltTest` walks the whole setup wizard because that is what it tests; it costs
 * ~140s and ends on **Virtual Pump**, so no pump-driver UI is ever rendered. This test wants the
 * pump screens, not the wizard, so it seeds that end state directly — mg/dL units, the wizard marked
 * done, an active local profile, and a paired Dana-i as the active pump — in a few seconds instead
 * of a second wizard walk. Note that what is seeded through preferences and what has to go through a
 * repository API differs; see `seedLocalProfile`.
 *
 * ## Fragility (read before editing)
 * Same rules as the other in-process E2E: selectors match **case-insensitively against text OR
 * content-desc** and match whole strings (so "Save" will not find "Save options to pump"), opens are
 * **verified-with-retry**, and it is English-only. Two traps cost real time here and are documented
 * where they bite: the pump screens are reached via Manage → Pump, *not* the bottom bar's setup
 * button ([openDanaPlugin]), and the Dana overview's action list vanishes and returns while a status
 * read is in flight, so every interaction with it waits for [waitForQueueIdle] first.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ShardA
class DanaRsEmulatorUiTest : AbstractDanaEmulatorUiTest() {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky UI timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var danaRSPlugin: DanaRSPlugin

    private lateinit var emulator: EmulatorBleTransport

    // ---- pump-specific hooks --------------------------------------------------------------------

    override fun injectHilt() = hiltRule.inject()

    override fun seedPairedPump(variant: ExternalOptions) {
        seedPairedDanaPump(variant)
        seedDanaAsActivePump()
    }

    override fun tearDownPump() {
        runCatching { danaRSPlugin.disconnect("test end") }
        // Disconnect first, then await: the emulator defers some responses onto their own threads
        // (v1's pairing key most of all); sendResponse drops them once disconnected, and this makes
        // sure they are actually done before the next test seeds a fresh pump.
        runCatching { if (::emulator.isInitialized) emulator.awaitPendingCallbacks() }
        // Unbind before the Hilt component dies, or the service crashes the process.
        runCatching { danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, false) }
    }

    override fun requestStatusRead() {
        danaRSPlugin.changePump()
    }

    /**
     * Fails before the UI is involved if [seedDanaAsActivePump] did not take — a Virtual Pump here
     * would otherwise surface much later as an unhelpful "Pump management opened the wrong screen".
     *
     * Deliberately says nothing about `isInitialized`: the pump initializes itself shortly after the
     * activity launches, so its value at any given instant is a race (see [openDanaPlugin]).
     */
    override fun assertActivePumpIsThisDana() {
        val pump = activePlugin.activePumpInternal as PluginBase
        assertThat(pump).isInstanceOf(DanaRSPlugin::class.java)
        assertThat(pump.hasComposeContent()).isTrue()
    }

    override fun lastBolusAmount(): Double = emulator.pumpState.lastBolusAmount
    override fun isTempBasalRunning(): Boolean = emulator.pumpState.isTempBasalRunning
    override fun tempBasalPercent(): Int = emulator.pumpState.tempBasalPercent
    override fun isExtendedBolusRunning(): Boolean = emulator.pumpState.isExtendedBolusRunning
    override fun extendedBolusAmount(): Double = emulator.pumpState.extendedBolusAmount
    override fun deliveredHistoryCodes(): List<Int> = emulator.pumpState.historyStore.getEventsAfter(0).map { it.code }

    // ---- RS pairing seed ------------------------------------------------------------------------

    /**
     * The preferences a completed pairing leaves behind, for [variant]'s handshake.
     *
     * The device name/address/password are shared; only the encryption keys differ per handshake
     * (see [seedPairingFor]). Seeding the matching keys is also what lets the driver reconnect
     * without a PIN/password screen no test could answer.
     */
    private fun seedPairedDanaPump(variant: ExternalOptions) {
        preferences.put(DanaStringNonKey.RsName, DEVICE_NAME)
        preferences.put(DanaStringNonKey.MacAddress, DEVICE_ADDRESS)
        preferences.put(DanaStringNonKey.Password, PASSWORD)

        emulator = bleTransport as EmulatorBleTransport
        seedPairingFor(variant)
        emulator.pairingDelayMs = 0
        emulator.writeLatencyMs = 0
        // A value no default produces, so finding it on screen can only mean it came from the pump
        // (see refreshStatusFromPump). The emulator's own default here is 25.
        emulator.pumpState.maxDailyTotalUnits = MAX_DAILY_UNITS
        seedPumpHistory()
    }

    /** Seeds the app-side encryption keys for [variant], matching the emulator's own defaults. */
    private fun seedPairingFor(variant: ExternalOptions) {
        when (variant) {
            ExternalOptions.EMULATE_DANA_BLE5  -> {
                preferences.put(DanaStringComposedKey.Ble5PairingKey, DEVICE_NAME, value = BLE5_PAIRING_KEY)
                // Pump side of the same key — a mismatch fails the handshake rather than the assertion.
                emulator.pumpState.ble5PairingKey = BLE5_PAIRING_KEY
            }

            ExternalOptions.EMULATE_DANA_RS_V3 -> {
                val encoder = Base64.getEncoder()
                val state = emulator.pumpState
                preferences.put(DanaStringComposedKey.V3ParingKey, DEVICE_NAME, value = encoder.encodeToString(state.v3PairingKey))
                preferences.put(DanaStringComposedKey.V3RandomParingKey, DEVICE_NAME, value = encoder.encodeToString(state.v3RandomPairingKey))
                preferences.put(DanaStringComposedKey.V3RandomSyncKey, DEVICE_NAME, value = String.format("%02x", state.v3RandomSyncKey))
            }

            // v1 (ENCRYPTION_DEFAULT) pairs on the password alone — no stored keys.
            else                               -> Unit
        }
    }

    /**
     * One alarm record on the emulated pump, for [visitDanaHistory] to load.
     *
     * An alarm because the history screen opens on that type (`DanaHistoryViewModel` selects the
     * first available), so the test never has to pick a chip. Timestamped in the past because the
     * driver asks for everything *after* a "from" instant and the store compares strictly.
     */
    private fun seedPumpHistory() {
        emulator.pumpState.reviewHistoryStore.addEvent(
            code = ReviewRecordCodes.ALARM,
            timestamp = dateUtil.now() - HISTORY_RECORD_AGE_MS,
            param1 = 0,
            param2 = ReviewRecordCodes.Alarm.OCCLUSION
        )
    }

    /**
     * Makes Dana-i the active pump instead of the default Virtual Pump, the way the Config Builder
     * persists it: `ConfigBuilder_Enabled_<TYPE>_<PluginClass>`. `ConfigBuilderImpl.loadSettings`
     * reads these in `initialize()` and `verifySelectionInCategories()` then elects the single
     * enabled pump. Everything here hangs off *active*: Manage → Pump opens whichever pump that is.
     */
    private fun seedDanaAsActivePump() {
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_DanaRSPlugin", value = true)
        preferences.put(BooleanComposedKey.ConfigBuilderEnabled, "PUMP_VirtualPumpPlugin", value = false)
    }

    // ---- tests ----------------------------------------------------------------------------------

    /**
     * One test rather than several: reaching the pump screens costs a connection and a status read,
     * and @Before would repeat that per test — while the legs are ordered anyway (nothing is
     * reachable until the pump reports initialized). Each leg says what failed on its way out.
     */
    @Test
    fun danaPumpUi_readsAndWritesTheEmulatedPump() {
        bringUp(ExternalOptions.EMULATE_DANA_BLE5)
        assertActivePumpIsThisDana()
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openDanaPlugin()          // Manage → Pump → DanaRSOverviewScreen
            assertVisible("Unpair")   // the pump reads as paired from the seeded preferences

            initializePumpFromUi()    // status read against the emulator → the rest of the actions

            refreshStatusFromPump()            // Refresh → status read → pump values on screen
            visitDanaHistory()                 // Pump history → DanaHistoryScreen
            changeUserOptionsAndSaveToPump()   // User options → edit → write back to the emulator

            device.pressBack()                 // Dana screens → the main overview
            deliverInsulinFromUi()             // bolus + temp basal + extended bolus → the emulator
        } catch (t: Throwable) {
            logScreen("E2E_DANA_SCREEN")
            logPumpState("E2E_DANA_STATE")
            throw t
        } finally {
            scenario.close()
        }
    }

    /**
     * The same insulin-delivery flow over the RSv1 (ENCRYPTION_DEFAULT) handshake.
     *
     * The flow is pump-agnostic — Manage → command queue → active pump — so only the handshake and
     * pairing seed differ from the BLE5 test above. That is exactly what makes these E2E flows worth
     * more than a unit test: the driver code they exercise is shared across every Dana variant, so
     * the *same* body proves each one for free (see [deliverInsulinFromUi]).
     */
    @Test
    fun danaInsulinDelivery_overV1Handshake() = runInsulinDeliveryOnly(ExternalOptions.EMULATE_DANA_RS_V1)

    /** The same insulin-delivery flow over the RSv3 (stateful randomSyncKey) handshake. */
    @Test
    fun danaInsulinDelivery_overV3Handshake() = runInsulinDeliveryOnly(ExternalOptions.EMULATE_DANA_RS_V3)

    /**
     * Brings [variant] up, initializes it through the UI, then runs the pump-agnostic
     * insulin-delivery flow — no RS-specific screens (those are covered once, on BLE5, above).
     */
    private fun runInsulinDeliveryOnly(variant: ExternalOptions) {
        bringUp(variant)
        assertActivePumpIsThisDana()
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()
            openDanaPlugin()          // Manage → Pump → DanaRSOverviewScreen
            initializePumpFromUi()    // status read against the emulator → the rest of the actions
            device.pressBack()        // Dana screen → the main overview
            deliverInsulinFromUi()
        } catch (t: Throwable) {
            logScreen("E2E_DANA_SCREEN")
            logPumpState("E2E_DANA_STATE")
            throw t
        } finally {
            scenario.close()
        }
    }

    // ---- RS-only screen legs --------------------------------------------------------------------

    /**
     * Dana overview → Refresh, which resets `DanaPump` and re-reads status through the command
     * queue (`DanaOverviewViewModel.onRefreshClick`).
     *
     * Waits for [MAX_DAILY_UNITS] — seeded onto the emulator and matching no default in the app —
     * to reach the screen. That makes this the pump → UI direction end to end: the reset means the
     * number cannot be left over from the earlier read, it has to be fetched again.
     */
    private fun refreshStatusFromPump() {
        repeat(REFRESH_ATTEMPTS) {
            waitForQueueIdle()   // tap into a settled list, not one mid-reset
            click("Refresh")     // this itself resets the pump and queues the read
            if (waitForVisible(MAX_DAILY_UNITS_TEXT, REFRESH_TIMEOUT)) {
                waitForQueueIdle()   // let the read finish before the next step navigates
                return
            }
            cancelStrayUnpairDialog()
        }
        error("Refresh never brought '$MAX_DAILY_UNITS_TEXT' back from the pump")
    }

    /**
     * Undoes a Refresh tap that landed on Unpair — a belt-and-braces guard now that [waitForQueueIdle]
     * settles the list first.
     *
     * The overview's actions are `visible = isInitialized`, so a tap that lands mid-read (the list
     * collapsing as the pump resets) can hit Unpair's coordinates instead and open this dialog.
     * Cancel it and let the caller retry — going through with an unpair would strip the seeded
     * pairing and fail everything after it for an unrelated reason.
     */
    private fun cancelStrayUnpairDialog() {
        if (device.findObject(byText("Reset pairing information?")) != null) {
            withStaleRetry { find("Cancel").click() }
            device.waitForIdle(IDLE_MS)
        }
    }

    /**
     * Overview → Pump history → Refresh, which loads the type's records off the pump and renders
     * them (`REVIEW__ALARM` here → `DanaRSPacketHistory` → `DanaHistoryRecordDao` → the screen).
     *
     * Opening asserts on the "Alarms" chip rather than the screen's "Refresh" button: the Dana
     * overview has a "Refresh" of its own, so a tap that never landed would still satisfy it.
     * Then the [seedPumpHistory] record has to arrive — the screen starts on "No records found",
     * so this fails if the load returns nothing, which is what it did before the emulator learned
     * to serve the review-history commands.
     */
    private fun visitDanaHistory() {
        waitForQueueIdle()   // overview settled before leaving it
        openVia("Pump history", expect = "Alarms")
        openVia("Refresh", expect = HISTORY_ALARM_TEXT)
        returnToDanaOverview()
    }

    /**
     * Overview → User options: nudge "LCD on time" up one step and save it **to the pump**, then
     * back to the overview.
     *
     * The only leg of this test that writes. Everything else reads, and a read can pass against a
     * driver that quietly drops what the UI hands it — so this asserts the new value on the
     * emulator's own [PumpState], not on the screen that produced it: UI → command queue →
     * `DanaRSPlugin` → `DanaRSService` → `BLEComm` → emulator, end to end.
     */
    private fun changeUserOptionsAndSaveToPump() {
        waitForQueueIdle()   // overview settled before leaving it
        openVia("User options", expect = SAVE_USER_OPTIONS)

        val before = emulator.pumpState.lcdOnTimeSec
        // Three steppers share the "Increase" description (LCD on time, Backlight on time,
        // Shutdown); they are laid out in that order, so the first is LCD's. Its range is 5-240, so
        // one step up is always in bounds.
        withStaleRetry { device.findObjects(byDesc("Increase")).first().click() }
        click(SAVE_USER_OPTIONS)

        // Asserts only that it grew, not by how much: the step is the screen's business (5 today),
        // and pinning it here would fail this test for a UI tweak that broke nothing.
        val written = awaitTrue(SAVE_TIMEOUT) { emulator.pumpState.lcdOnTimeSec > before }
        assertThat(written).isTrue()

        // Saving may leave the screen on its own; only tap Back if it did not.
        if (!waitForVisible("Unpair", IDLE_MS)) returnToDanaOverview()
    }

    /**
     * Sub-screens return to DanaScreen.OVERVIEW via their **toolbar** back arrow.
     *
     * Not `device.pressBack()`: system back pops the whole PluginContent route off the NavHost and
     * lands on the AAPS overview, one screen too far — the Dana screens switch between themselves
     * with their own state, inside a single destination.
     */
    private fun returnToDanaOverview() {
        openVia("Back", expect = "Unpair")
    }

    /** The exact inputs ComposeMainActivity's `showPumpSetup` reads, sampled at failure time. */
    private fun logPumpState(tag: String) {
        runCatching {
            val pump = activePlugin.activePumpInternal as PluginBase
            android.util.Log.e(
                tag,
                "activePump=${pump.javaClass.simpleName} enabled=${pump.isEnabled()} " +
                    "composeContent=${pump.hasComposeContent()} initialized=${activePlugin.activePump.isInitialized()} " +
                    "suspended=${activePlugin.activePump.isSuspended()}"
            )
        }
        runCatching {
            val s = emulator.pumpState
            android.util.Log.e(
                tag,
                "emulator lcdOnTimeSec=${s.lcdOnTimeSec} backlightOnTimeSec=${s.backlightOnTimeSec} " +
                    "shutdownHour=${s.shutdownHour} beepAndAlarm=${s.beepAndAlarm}"
            )
        }
    }

    companion object {

        /** Selectors match whole strings, so this cannot be shortened to "Save". */
        private const val SAVE_USER_OPTIONS = "Save options to pump"

        // Mirrors BLECommBLE5IntegrationTest / DanaRsEmulatorPumpTest.
        private const val DEVICE_NAME = "UHH00002TI"
        private const val DEVICE_ADDRESS = "00:00:00:00:00:00"
        private const val BLE5_PAIRING_KEY = "474632"
        private const val PASSWORD = "0000"

        /** Seeded onto the emulator; the app's own defaults never produce it. */
        private const val MAX_DAILY_UNITS = 42.0
        private const val MAX_DAILY_UNITS_TEXT = "42.00 U"

        /** The alarm seeded onto the pump, as DanaRSPacketHistory names it for the screen. */
        private const val HISTORY_ALARM_TEXT = "Occlusion"
        private const val HISTORY_RECORD_AGE_MS = 60 * 60 * 1000L

        private const val REFRESH_TIMEOUT = 30_000L
        private const val REFRESH_ATTEMPTS = 3
        private const val SAVE_TIMEOUT = 30_000L
    }
}
