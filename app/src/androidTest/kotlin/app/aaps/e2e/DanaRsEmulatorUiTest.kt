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
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.emulator.ReviewRecordCodes
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danars.emulator.EmulatorBleTransport
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern
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
 * The emulator is selected purely by [ExternalOptions.EMULATE_DANA_BLE5] — see [EmulatedOptions] for
 * why a test has to report that rather than drop the production marker file. BLE5 (Dana-i) because
 * its handshake is the simplest the emulator speaks: a stored pairing key, no passkey round-trip.
 * `DanaRsEmulatorPumpTest` covers the same stack headlessly; this one adds the screens on top.
 *
 * ## Why this is seeded rather than wizard-driven
 * `SetupWizardE2EHiltTest` walks the whole setup wizard because that is what it tests; it costs
 * ~140s and ends on **Virtual Pump**, so no pump-driver UI is ever rendered. This test wants the
 * pump screens, not the wizard, so it seeds that end state directly — mg/dL units, the wizard marked
 * done, an active local profile, and a paired Dana-i as the active pump — in a few seconds instead
 * of a second wizard walk. Note that what is seeded through preferences and what has to go through a
 * repository API differs; see [seedLocalProfile].
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
class DanaRsEmulatorUiTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var bleTransport: BleTransport
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var commandQueue: CommandQueue
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
        // Clear before the graph reads any prefs, exactly as SetupWizardE2EHiltTest does: singletons
        // then seed their defaults against empty prefs like a fresh app would.
        clearAllSharedPrefs()
        EmulatedOptions.enabled = setOf(ExternalOptions.EMULATE_DANA_BLE5)
        hiltRule.inject()

        // BLEComm.connect gates on this before it ever reaches the transport.
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        // BlePreCheckHost pops "Application needs bluetooth permission" over the pump screen without this.
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
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
        // Both after initialize(), which elects the active profile source these go through.
        seedLocalProfile()
        activateSeededProfile()

        // Deliberately NO changePump()/connect() here. Both would read pump status and mark the pump
        // initialized — and ComposeMainActivity only offers the bottom-bar button onto the pump's
        // screen while it is *not* initialized (showPumpSetup = !isInitialized || isSuspended). The
        // status read has to happen after the screen is open; see initializePumpFromUi.
        //
        // configBuilder.initialize() already enabled the plugin, whose onStart binds DanaRSService
        // on pluginScope. That bind is async but lands well inside this test's Hilt component, which
        // is what matters (see DanaRsEmulatorPumpTest); tearDown unbinds it before the component dies.

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

    /** Units + a completed wizard: the state the wizard would have written. */
    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
    }

    /**
     * Adds the local profile through [ProfileRepository], **not** by writing its preferences.
     *
     * `ProfileRepositoryImpl` reads those preferences once, from its `init` block — which
     * `hiltRule.inject()` has already run by the time any test code executes, so a profile seeded
     * into preferences here is never read and the store stays empty. Going through `add` also
     * persists it, so the result is the same state the profile editor would leave behind.
     *
     * [ProfileRepository.newDraft] names it (`LocalProfile1`) and zeroes every block, which is
     * deliberately invalid — fill them in or the profile switch below is rejected as invalid.
     */
    private fun seedLocalProfile() {
        val profile = profileRepository.newDraft().apply {
            mgdl = true
            ic = JSONArray(singleValue(10.0))
            isf = JSONArray(singleValue(50.0))
            basal = JSONArray(singleValue(0.5))
            targetLow = JSONArray(singleValue(100.0))
            targetHigh = JSONArray(singleValue(110.0))
        }
        check(profile.name == PROFILE_NAME) { "Expected the draft to be named $PROFILE_NAME, got ${profile.name}" }
        runBlocking { profileRepository.add(profile) }.getOrThrow()
    }

    /**
     * Activates the profile [seedLocalProfile] added, the way the profile UI does.
     *
     * Adding a profile only makes it *selectable*: without a ProfileSwitch the overview sits on
     * "NO PROFILE SET" and anything needing a profile is refused — including [bolusFromUi].
     */
    private fun activateSeededProfile() {
        // The store [seedLocalProfile] published. add() snapshots the StateFlow before returning,
        // but it hops to Dispatchers.IO on the way, so give the emit a moment to land.
        val store = checkNotNull(
            awaitNotNull(PROFILE_STORE_TIMEOUT) {
                profileRepository.profile.value?.takeIf { it.getSpecificProfile(PROFILE_NAME) != null }
            }
        ) { "The profile store never published '$PROFILE_NAME'" }

        // Mirrors ActionProfileSwitch: an indefinite 100% switch to the seeded profile, on the
        // running insulin config (this test does not vary insulin).
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
        checkNotNull(switch) {
            "Could not activate the seeded local profile '$PROFILE_NAME' — store offers ${store.getProfileList()}"
        }
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
        // A value no default produces, so finding it on screen can only mean it came from the pump
        // (see refreshStatusFromPump). The emulator's own default here is 25.
        emulator.pumpState.maxDailyTotalUnits = MAX_DAILY_UNITS
        seedPumpHistory()
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

    /** The profile-editor JSON shape: a single all-day value. */
    private fun singleValue(value: Double) =
        """[{"time":"00:00","timeAsSeconds":0,"value":$value}]"""

    /** Polls [supplier] until it returns non-null or [timeoutMs] elapses. */
    private fun <T> awaitNotNull(timeoutMs: Long, supplier: () -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            runCatching(supplier).getOrNull()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        return null
    }

    /** Polls [condition] until it returns true or [timeoutMs] elapses. */
    private fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    /**
     * One test rather than several: reaching the pump screens costs a connection and a status read,
     * and @Before would repeat that per test — while the legs are ordered anyway (nothing is
     * reachable until the pump reports initialized). Each leg says what failed on its way out.
     */
    @Test
    fun danaPumpUi_readsAndWritesTheEmulatedPump() {
        assertActivePumpIsDana()
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
            bolusFromUi()                      // Treatments → Insulin → bolus → the emulator
        } catch (t: Throwable) {
            logScreen("E2E_DANA_SCREEN")
            logPumpState("E2E_DANA_STATE")
            throw t
        } finally {
            scenario.close()
        }
    }

    /**
     * Fails before the UI is involved if [seedDanaAsActivePump] did not take — a Virtual Pump here
     * would otherwise surface much later as an unhelpful "Pump management opened the wrong screen".
     *
     * Deliberately says nothing about `isInitialized`: the pump initializes itself shortly after the
     * activity launches, so its value at any given instant is a race (see [openDanaPlugin]).
     */
    private fun assertActivePumpIsDana() {
        val pump = activePlugin.activePumpInternal as PluginBase
        assertThat(pump).isInstanceOf(DanaRSPlugin::class.java)
        assertThat(pump.hasComposeContent()).isTrue()
    }

    /**
     * Reads pump status through the command queue until the pump reports initialized.
     *
     * The overview can't bootstrap this itself: Refresh/Pump history/User options are all
     * `visible = isInitialized` (DanaOverviewViewModel), and the bottom-bar button that got us onto
     * this screen only shows while the pump is *not* initialized — so the first read has to come
     * from outside the UI. changePump() does exactly what the app does on a device change: queue a
     * readStatus. Once it completes against the emulator the actions appear.
     */
    private fun initializePumpFromUi() {
        if (!awaitTrue(INIT_PUMP_TIMEOUT) {
                // Only queue a read when the last one has finished. changePump() -> readStatus, and
                // readStatus resets DanaPump first (onRefreshClick -> danaPump.reset), so a fresh one
                // each poll would stack a backlog of reads that keep resetting the pump and churning
                // the action list long after this returns — which is what made the later steps flaky.
                if (queueIdle()) danaRSPlugin.changePump()
                waitForVisible("Pump history", 2_000)
            }
        ) error("Pump never reported initialized — 'Pump history' never appeared")
        waitForQueueIdle()
    }

    /**
     * True when no command is queued or running.
     *
     * The one deterministic "the overview has settled" signal: `isInitialized` is stable-true and
     * every action present exactly while nothing is in flight. A read in flight has just called
     * `danaPump.reset()`, so `isInitialized` is false and the whole action list is gone until it
     * completes — tapping into that window is what mis-fired onto Unpair and timed out on absent
     * buttons.
     */
    private fun queueIdle() = commandQueue.size() == 0 && commandQueue.performing() == null

    /** Blocks until [queueIdle], then lets the resulting recomposition land. */
    private fun waitForQueueIdle() {
        if (!awaitTrue(QUEUE_IDLE_TIMEOUT) { queueIdle() }) error("Command queue never went idle")
        device.waitForIdle(IDLE_MS)
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
     * Overview → Manage → Pump management, which renders the active pump's compose content
     * (`ComposeMainActivity.handlePluginClick`) — DanaRSOverviewScreen here, via
     * [seedDanaAsActivePump].
     *
     * Deliberately **not** the "Dana-i/RS" button in the bottom bar, which is the more obvious
     * route: `ComposeMainActivity` only offers that one while the pump is *not* initialized
     * (`showPumpSetup`), and the pump initializes itself moments after the activity launches. Which
     * of the two wins is a race the test cannot control — it lost locally (button never rendered)
     * and won in CI (build 40254), on identical code. Manage → Pump management has no such gate, and
     * is also the route a user with a working pump actually takes.
     *
     * Not the nav drawer either: that is a fixed menu (history/statistics/maintenance/...) with no
     * plugin entries.
     */
    private fun openDanaPlugin() {
        openVia("Manage", expect = PUMP_MANAGEMENT)
        openVia(PUMP_MANAGEMENT, expect = "Unpair")
    }

    /**
     * The main overview → Treatments → Insulin → a [BOLUS_UNITS] bolus, confirmed and delivered to
     * the emulated pump.
     *
     * The one leg that leaves the pump plugin's own screens, and the reason the profile has to be
     * real (see [activateSeededProfile]) — the dialog refuses to bolus without one. This is the
     * path a user actually boluses through, and the most safety-critical one in the app:
     * InsulinDialog → `CommandQueue.bolus` → QueueWorker → `DanaRSPlugin.deliverTreatment` →
     * `DanaRSService` → `BLEComm` → emulator.
     *
     * Asserted on the emulator's own `lastBolusAmount`, so it fails if the driver delivers nothing,
     * or delivers the wrong dose.
     */
    private fun bolusFromUi() {
        assertThat(emulator.pumpState.lastBolusAmount).isEqualTo(0.0)

        openVia("Treatments", expect = "Insulin")
        openVia("Insulin", expect = BOLUS_CHIP)
        click(BOLUS_CHIP)
        // The confirm button is labelled "OK" only while no dose is set; picking one relabels it to
        // the dose itself (InsulinDialogScreen), which doubles as proof the chip registered.
        click(BOLUS_CONFIRM)
        click("OK")               // confirmation dialog → deliver

        val delivered = awaitTrue(BOLUS_TIMEOUT) { emulator.pumpState.lastBolusAmount == BOLUS_UNITS }
        assertThat(delivered).isTrue()
    }

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

    private fun assertVisible(label: String, timeout: Long = STEP_TIMEOUT) {
        find(label, timeout)
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

        /** `core.ui.R.string.pump_management` — the Manage sheet's entry onto the active pump. */
        private const val PUMP_MANAGEMENT = "Pump"

        /** Selectors match whole strings, so this cannot be shortened to "Save". */
        private const val SAVE_USER_OPTIONS = "Save options to pump"
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
        /** Seeded onto the emulator; the app's own defaults never produce it. */
        private const val MAX_DAILY_UNITS = 42.0
        private const val MAX_DAILY_UNITS_TEXT = "42.00 U"

        /** The alarm seeded onto the pump, as DanaRSPacketHistory names it for the screen. */
        private const val HISTORY_ALARM_TEXT = "Occlusion"
        private const val HISTORY_RECORD_AGE_MS = 60 * 60 * 1000L

        private const val INIT_PUMP_TIMEOUT = 60_000L
        private const val REFRESH_TIMEOUT = 30_000L
        private const val REFRESH_ATTEMPTS = 3
        private const val QUEUE_IDLE_TIMEOUT = 60_000L
        private const val SAVE_TIMEOUT = 30_000L
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val BOLUS_TIMEOUT = 60_000L

        /** The insulin dialog's middle quick-add button (DoubleKey.OverviewInsulinButtonIncrement2 default). */
        private const val BOLUS_UNITS = 1.0
        private const val BOLUS_CHIP = "+1.00"

        /** `core.ui.R.string.format_insulin_units` for [BOLUS_UNITS] — the confirm button once a dose is set. */
        private const val BOLUS_CONFIRM = "1.00 U"
        private const val POLL_MS = 250L
    }
}
