package app.aaps.e2e

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.di.EmulatedOptions
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.pump.dana.DanaPump
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Pump-agnostic base for the Dana **UI** E2E tests: everything that reaches the emulated pump through
 * the whole production stack — UI → `CommandQueue` → active pump plugin → its service → the transport
 * — without touching a pump-specific field. Concrete subclasses (one per Dana family, e.g. Dana-i/RS)
 * supply the pump-specific wiring through the abstract hooks below and add their own `@Test` methods.
 *
 * The insulin-delivery flow ([deliverInsulinFromUi]) is pump-agnostic — Manage → command queue →
 * active pump — so the *same* body proves every Dana variant, which is the reuse that makes an E2E
 * worth more than a per-plugin unit test.
 *
 * ## Fragility (read before editing)
 * Same rules as the other in-process E2E: selectors match **case-insensitively against text OR
 * content-desc** and match whole strings (so "Save" will not find "Save options to pump"), opens are
 * **verified-with-retry**, and it is English-only. Two traps cost real time here and are documented
 * where they bite: the pump screens are reached via Manage → Pump, *not* the bottom bar's setup
 * button ([openDanaPlugin]), and the Dana overview's action list vanishes and returns while a status
 * read is in flight, so every interaction with it waits for [waitForQueueIdle] first.
 *
 * The `@Inject` fields here are inherited: Hilt injects them when the concrete subclass calls
 * `hiltRule.inject()` (via [injectHilt]).
 */
abstract class AbstractDanaEmulatorUiTest {

    // Public rather than protected: Dagger's generated member injector cannot write Kotlin
    // `protected` fields. Still accessible to subclasses as inherited members.
    @Inject lateinit var preferences: Preferences
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

    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    // ---- pump-specific hooks (implemented per Dana family) --------------------------------------

    /** Subclass body: `hiltRule.inject()`. Kept a hook because the `HiltAndroidRule` lives on the concrete test. */
    protected abstract fun injectHilt()

    /** Seeds the transport/pairing/active-pump/maxDaily/history state for [variant] on the concrete pump. */
    protected abstract fun seedPairedPump(variant: ExternalOptions)

    /** Disconnects and unbinds the concrete pump before the Hilt component dies. */
    protected abstract fun tearDownPump()

    /** Queues a status read the way the app does on a device change (subclass: `changePump()`). */
    protected abstract fun requestStatusRead()

    /** Fails before the UI is involved if the active pump is not this Dana family. */
    protected abstract fun assertActivePumpIsThisDana()

    /** The emulated pump's last delivered bolus amount. */
    protected abstract fun lastBolusAmount(): Double

    /** Whether a temp basal is running on the emulated pump. */
    protected abstract fun isTempBasalRunning(): Boolean

    /** The emulated pump's current temp-basal percent. */
    protected abstract fun tempBasalPercent(): Int

    /** Whether an extended bolus is running on the emulated pump. */
    protected abstract fun isExtendedBolusRunning(): Boolean

    /** The emulated pump's current extended-bolus amount. */
    protected abstract fun extendedBolusAmount(): Double

    /** The event codes recorded on the emulated pump's APS history store. */
    protected abstract fun deliveredHistoryCodes(): List<Int>

    @Before
    fun setUp() {
        // Clear before the graph reads any prefs — the variant is chosen per test in bringUp().
        clearAllSharedPrefs()
    }

    /**
     * Brings the app up with [variant] as the paired, active Dana pump.
     *
     * Per test rather than `@Before` because the variant has to be set *before* `hiltRule.inject()`:
     * `BleTransport` is `@Singleton`, so `DanaModules` reads which `EMULATE_*` option is on once,
     * when the graph first constructs it. The whole insulin-delivery flow below is pump-agnostic
     * (Manage → command queue → active pump), so the same flow runs against every RS handshake — only
     * this seeding differs (see each subclass's `seedPairedPump`).
     */
    protected fun bringUp(variant: ExternalOptions) {
        EmulatedOptions.enabled = setOf(variant)
        injectHilt()

        // BLEComm.connect gates on this before it ever reaches the transport.
        instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_CONNECT)
        // BlePreCheckHost pops "Application needs bluetooth permission" over the pump screen without this.
        runCatching { instrumentation.uiAutomation.grantRuntimePermission(PKG, Manifest.permission.BLUETOOTH_SCAN) }
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS")
        }

        seedConfiguredApp()
        seedPairedPump(variant)

        pluginStore.plugins = pluginList
        // Reads the ConfigBuilderEnabled preferences seeded above, then verifySelectionInCategories()
        // makes Dana the active pump. Must come after seeding: initialize() resolves the active pump once.
        configBuilder.initialize()
        config.initCompleted()
        // Both after initialize(), which elects the active profile source these go through.
        seedLocalProfile()
        activateSeededProfile()

        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        device.executeShellCommand("logcat -c")
    }

    @After
    fun tearDown() {
        // This class now runs three tests (BLE5/v1/v3). Any command-queue work, WorkManager job or
        // deferred emulator callback left in flight keeps talking to the pump into whichever test
        // starts next, whose UI then recomposes under uiautomator and dies with a StaleObjectException
        // (see the same note in DanaRsEmulatorPumpTest). Drain everything before the component dies.
        runCatching { commandQueue.clear() }
        runCatching { WorkManager.getInstance(instrumentation.targetContext).cancelAllWork() }
        tearDownPump()
        EmulatedOptions.enabled = emptySet()
        clearAllSharedPrefs()
    }

    /** Units + a completed wizard: the state the wizard would have written. */
    private fun seedConfiguredApp() {
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, true)
        preferences.put(StringKey.GeneralUnits, "mg/dl")
        // Simple mode (the default) hides the Temp basal / Extended bolus actions in the Manage
        // sheet (ManageBottomSheet gates them on !isSimpleMode). Turn it off so those are reachable.
        preferences.put(BooleanKey.GeneralSimpleMode, false)
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
    protected fun awaitTrue(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    /** Bolus, temp basal, extended bolus through the UI, each asserted against the emulator. */
    protected fun deliverInsulinFromUi() {
        bolusFromUi()                      // Treatments → Insulin → bolus → the emulator
        applyTempBasalFromUi()             // Manage → Temp basal → the emulator (TEMP_START)
        applyExtendedBolusFromUi()         // Manage → Extended bolus → the emulator (EXTENDED_START)
        readBackDeliveredHistory()         // the same deliveries, read back off the pump as history
    }

    /**
     * The reverse direction: every treatment the UI just delivered is recorded on the emulated pump's
     * APS event history, so it can be read back as history — and the driver already does, on the
     * status read each following command runs (`DanaRSService.loadEvents` → `DanaRSPacketAPSHistoryEvents`),
     * parsing and syncing these very events (the `**NEW** EVENT …` log lines). Asserted here on the
     * pump's own store, the true far side, so it holds for every handshake the flow runs over.
     *
     * These are events the test *generated*, not seeded ones — which is the whole point. A hand-planted
     * past bolus would carry an encoded time in the current minute that the live UI bolus then reads as
     * a duplicate and drops; real deliveries carry their real times and nothing live follows to collide.
     */
    private fun readBackDeliveredHistory() {
        val codes = deliveredHistoryCodes()
        assertThat(codes).contains(DanaPump.HistoryEntry.BOLUS.value)
        assertThat(codes).contains(DanaPump.HistoryEntry.TEMP_START.value)
        assertThat(codes).contains(DanaPump.HistoryEntry.EXTENDED_START.value)
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
    protected fun initializePumpFromUi() {
        if (!awaitTrue(INIT_PUMP_TIMEOUT) {
                // Only queue a read when the last one has finished. changePump() -> readStatus, and
                // readStatus resets DanaPump first (onRefreshClick -> danaPump.reset), so a fresh one
                // each poll would stack a backlog of reads that keep resetting the pump and churning
                // the action list long after this returns — which is what made the later steps flaky.
                if (queueIdle()) requestStatusRead()
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
    protected fun queueIdle() = commandQueue.size() == 0 && commandQueue.performing() == null && !queueWorkerRunning()

    /**
     * Whether WorkManager still has the command-queue worker alive.
     *
     * The queue emptying (`size()==0 && performing==null`) is *not* the same as the worker being
     * gone: after its last command the `QueueWorker` still runs a "queue empty → disconnect → exit"
     * tail, during which WorkManager reports it RUNNING. A command added in that window is stranded —
     * `CommandQueueImplementation.notifyAboutNewCommand` skips scheduling a new worker while one is
     * still running (`if (!workIsRunning())`), and the dying worker never re-polls the queue. That is
     * the intermittent "bolus recorded but never delivered" flake, so idle has to mean the worker is
     * actually finished too. Mirrors `CommandQueueImplementation.workIsRunning`.
     */
    private fun queueWorkerRunning(): Boolean =
        WorkManager.getInstance(instrumentation.targetContext)
            .getWorkInfosForUniqueWork(QUEUE_WORK_NAME).get()
            .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    /** Blocks until [queueIdle], then lets the resulting recomposition land. */
    protected fun waitForQueueIdle() {
        if (!awaitTrue(QUEUE_IDLE_TIMEOUT) { queueIdle() }) error("Command queue never went idle")
        device.waitForIdle(IDLE_MS)
    }

    // ---- navigation ---------------------------------------------------------------------------

    /**
     * Waits for the overview, dismissing the "Permissions Required" sheet AAPS re-shows on resume
     * until the AAPS directory is granted — it covers the toolbar, so it has to go before anything
     * can be tapped. Re-checked each pass because it can reappear.
     */
    protected fun waitForOverview() {
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            dismissBlockingSheetIfPresent()
            if (device.findObject(byDesc("Open navigation")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Overview never appeared within ${INIT_TIMEOUT}ms — UI stuck on splash or behind a sheet")
    }

    private fun dismissBlockingSheetIfPresent() {
        // The sheet animates in/out, so the node can invalidate between find and click — swallow the
        // stale hit and let waitForOverview's loop retry rather than fail the whole test on it. (Seen
        // only under package-run load; harmless in isolation.)
        runCatching { device.findObject(byDesc("Close sheet"))?.click() }
    }

    /**
     * Overview → Manage → Pump management, which renders the active pump's compose content
     * (`ComposeMainActivity.handlePluginClick`) — the Dana overview screen, reachable because the
     * subclass's `seedPairedPump` makes this Dana the active pump.
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
    protected fun openDanaPlugin() {
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
    /**
     * The insulin quick-add chip label, formatted to the active pump's bolus step (RS 0.05/0.01 → two
     * decimals "+1.00"; DanaR 0.1 → one decimal "+1.0"), so overridden per pump. The dose-confirm
     * button uses `format_insulin_units` (always two decimals, "1.00 U") regardless of pump, so it is
     * open only for symmetry and defaults correctly for both.
     */
    protected open val bolusChipLabel: String get() = "+1.00"
    protected open val bolusConfirmLabel: String get() = "1.00 U"

    private fun bolusFromUi() {
        // The lean insulin-delivery flow reaches here right after initialization, while its status
        // reads may still be draining; the full flow settled during the RS-screen legs. Bolusing into
        // that window enqueues nothing (the command is dropped mid-reconnect) and the assertion below
        // times out. Wait for the queue to drain first, exactly as the temp-basal/extended legs do.
        waitForQueueIdle()
        assertThat(lastBolusAmount()).isEqualTo(0.0)

        openVia("Treatments", expect = "Insulin")
        openVia("Insulin", expect = bolusChipLabel)
        click(bolusChipLabel)
        // The confirm button is labelled "OK" only while no dose is set; picking one relabels it to
        // the dose itself (InsulinDialogScreen), which doubles as proof the chip registered.
        click(bolusConfirmLabel)
        click("OK")               // confirmation dialog → deliver

        val delivered = awaitTrue(BOLUS_TIMEOUT) { lastBolusAmount() == BOLUS_UNITS }
        assertThat(delivered).isTrue()
    }

    /**
     * Overview → Manage → Temp basal: raise the rate above 100% and commit it to the pump.
     *
     * Drives `setTempBasalPercent` end to end — the Manage sheet's TBR dialog → command queue →
     * `DanaRSPlugin` → `DanaRSService` → emulator — and asserts the temp basal on the emulator's own
     * state, not the driver's. Only reachable once the pump is initialized (Manage gates the action
     * on it), which the earlier legs establish.
     */
    private fun applyTempBasalFromUi() {
        assertThat(isTempBasalRunning()).isFalse()
        waitForQueueIdle()
        openManageAction("Temp basal")
        openVia("Temp basal", expect = "Decrease")   // the TBR dialog (its steppers)
        // Raise the percent above 100 so the confirm button enables; the duration is pre-set to the
        // pump's step. The first "Increase" stepper is the percent (duration is the second).
        repeat(TBR_INCREASE_TAPS) { withStaleRetry { device.findObjects(byDesc("Increase")).first().click() } }
        clickRegex("""\d+\s*%""")   // confirm button, labelled with the chosen percent
        click("OK")                 // ElementConfirmationDialog → commit
        val applied = awaitTrue(COMMAND_TIMEOUT) {
            isTempBasalRunning() && tempBasalPercent() > 100
        }
        assertThat(applied).isTrue()
        waitForQueueIdle()
    }

    /**
     * Overview → Manage → Extended bolus: set an amount and commit it to the pump.
     *
     * Drives `setExtendedBolus` end to end, asserted on the emulator's `extendedBolusAmount`.
     */
    private fun applyExtendedBolusFromUi() {
        assertThat(isExtendedBolusRunning()).isFalse()
        waitForQueueIdle()
        openManageAction("Extended bolus")
        openVia("Extended bolus", expect = "Decrease")
        // First "Increase" stepper is the insulin amount; raise it above 0 to enable the confirm.
        repeat(EXTENDED_INCREASE_TAPS) { withStaleRetry { device.findObjects(byDesc("Increase")).first().click() } }
        clickRegex("""\d+\.\d+\s*U""")   // confirm button, labelled with the chosen amount
        click("OK")
        val applied = awaitTrue(COMMAND_TIMEOUT) {
            isExtendedBolusRunning() && extendedBolusAmount() > 0.0
        }
        assertThat(applied).isTrue()
        waitForQueueIdle()
    }

    protected fun assertVisible(label: String, timeout: Long = STEP_TIMEOUT) {
        find(label, timeout)
    }

    // ---- ui helpers (same contract as SetupWizardE2EHiltTest) -----------------------------------

    protected fun byText(s: String): BySelector =
        By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

    /** Whole-node text match against a raw regex (e.g. the TBR/extended confirm button, labelled with its value). */
    protected fun byTextRegex(regex: String): BySelector = By.text(Pattern.compile(regex))

    protected fun clickRegex(regex: String, timeout: Long = STEP_TIMEOUT) = withStaleRetry {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byTextRegex(regex))?.let { it.click(); return@withStaleRetry }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out looking for a node matching /$regex/")
    }

    protected fun byDesc(s: String): BySelector =
        By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

    protected fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    protected fun click(label: String) = withStaleRetry { find(label).click() }

    /**
     * Opens the Manage sheet and taps [action], scrolling the sheet to reach it.
     *
     * Temp basal / Extended bolus sit near the bottom of `ManageBottomSheet`, below the fold, so a
     * plain find never sees them — the sheet has to be scrolled first.
     */
    private fun openManageAction(action: String) {
        repeat(OPEN_ATTEMPTS) {
            click("Manage")
            if (scrollSheetToFind(action)) {
                click(action)
                return
            }
            device.pressBack() // close the sheet and retry from the overview
            device.waitForIdle(IDLE_MS)
        }
        error("'$action' not found in the Manage sheet")
    }

    /** Swipes the open bottom sheet up until [label] is visible. */
    private fun scrollSheetToFind(label: String, maxSwipes: Int = 6): Boolean {
        if (waitForVisible(label, IDLE_MS)) return true
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(maxSwipes) {
            device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.3).toInt(), 20)
            device.waitForIdle(IDLE_MS)
            if (device.findObject(byText(label)) != null) return true
        }
        return false
    }

    protected fun openVia(open: String, expect: String, attempts: Int = 4) {
        repeat(attempts) {
            click(open)
            if (waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on '$open'")
    }

    protected fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    /** Compose recomposes frequently on a slow emulator, invalidating nodes between find and click. */
    protected inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
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

    protected fun logScreen(tag: String) {
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

        const val PKG = "info.nightscout.androidaps"

        /** `core.ui.R.string.pump_management` — the Manage sheet's entry onto the active pump. */
        private const val PUMP_MANAGEMENT = "Pump"

        private const val PROFILE_NAME = "LocalProfile1"

        private const val INIT_TIMEOUT = 60_000L
        private const val STEP_TIMEOUT = 30_000L
        // Non-private: referenced from subclass legs and inlined into subclasses via withStaleRetry.
        const val IDLE_MS = 300L
        const val STALE_RETRIES = 10
        const val STALE_SETTLE_MS = 700L

        private const val INIT_PUMP_TIMEOUT = 60_000L
        private const val QUEUE_IDLE_TIMEOUT = 60_000L
        /** The command queue's WorkManager unique-work name (CommandQueueModule.commandQueueJobName). */
        private const val QUEUE_WORK_NAME = "CommandQueue"
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val BOLUS_TIMEOUT = 60_000L
        private const val COMMAND_TIMEOUT = 60_000L
        private const val TBR_INCREASE_TAPS = 3
        private const val EXTENDED_INCREASE_TAPS = 3
        private const val OPEN_ATTEMPTS = 3


        /** The insulin dialog's middle quick-add button (DoubleKey.OverviewInsulinButtonIncrement2 default).
         *  Its on-screen label ("+1.00"/"+1.0") is pump-step-dependent — see [bolusChipLabel]. */
        private const val BOLUS_UNITS = 1.0
        private const val POLL_MS = 250L
    }
}
