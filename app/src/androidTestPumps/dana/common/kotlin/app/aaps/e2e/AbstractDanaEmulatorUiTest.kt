package app.aaps.e2e

import android.Manifest
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import androidx.work.WorkManager
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.di.EmulatedOptions
import app.aaps.di.testGraphs
import app.aaps.e2e.AbstractDanaEmulatorUiTest.Companion.DECREMENT
import app.aaps.e2e.AbstractDanaEmulatorUiTest.Companion.INCREMENT
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import java.util.regex.Pattern

/**
 * Pump-agnostic base for the Dana **UI** E2E tests: everything that reaches the emulated pump through
 * the whole production stack — UI → `CommandQueue` → active pump plugin → its service → the transport
 * — without touching a pump-specific field. Concrete subclasses (one per Dana family, e.g. Dana-i/RS)
 * supply the pump-specific wiring through the abstract hooks below and add their own `@Test` methods.
 * The shared treatment dialogs (bolus, temp basal, extended bolus) get one pass, in `DanaRsEmulatorUiTest`:
 * each driver's commands are tested through the queue by its scenario class.
 * ## Fragility (read before editing)
 * Same rules as the other in-process E2E: selectors match **case-insensitively against text OR
 * content-desc** and match whole strings (so "Save" will not find "Save options to pump"), opens are
 * **verified-with-retry**, and it is English-only. Two traps cost real time here and are documented
 * where they bite: the pump screens are reached via Manage → Pump, *not* the bottom bar's setup
 * button ([openDanaPlugin]), and the Dana overview's action list vanishes and returns while a status
 * read is in flight, so every interaction with it waits for [waitForQueueIdle] first.
 */
abstract class AbstractDanaEmulatorUiTest {

    protected val preferences get() = testGraphs.preferences
    protected val pluginStore get() = testGraphs.pluginStore
    protected val commandQueue get() = testGraphs.commandQueue
    protected val pluginList get() = testGraphs.allPlugins(testGraphs.aapsLogger)
    protected val configBuilder get() = testGraphs.configBuilder
    protected val profileFunction get() = testGraphs.profileFunction
    protected val profileRepository get() = testGraphs.profileRepository
    protected val insulinManager get() = testGraphs.insulinManager
    protected val dateUtil get() = testGraphs.dateUtil
    protected val activePlugin get() = testGraphs.activePlugin
    protected val config get() = testGraphs.config

    protected val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    // ---- pump-specific hooks (implemented per Dana family) --------------------------------------


    /** Seeds the transport/pairing/active-pump/maxDaily/history state for [variant] on the concrete pump. */
    protected abstract fun seedPairedPump(variant: ExternalOptions)

    /** Disconnects and unbinds the concrete pump before the graph dies. */
    protected abstract fun tearDownPump()

    /** Queues a status read the way the app does on a device change (subclass: `changePump()`). */
    protected abstract fun requestStatusRead()

    /** Fails before the UI is involved if the active pump is not this Dana family. */
    protected abstract fun assertActivePumpIsThisDana()

    @Before
    fun setUp() {
        // Clear before the graph reads any prefs — the variant is chosen per test in bringUp().
        clearAllSharedPrefs()
    }

    /**
     * Brings the app up with [variant] as the paired, active Dana pump.
     * Per test rather than `@Before` because the variant has to be set *before* the graph is built:
     * `BleTransport` is `@Singleton`, so `DanaModules` reads which `EMULATE_*` option is on once,
     * when the graph first constructs it. Only this seeding differs between the variants (see each
     * subclass's `seedPairedPump`). It starts the driver too, so a test that needs no screen can go on
     * with [initializePumpThroughQueue].
     */
    protected fun bringUp(variant: ExternalOptions) {
        EmulatedOptions.enabled = setOf(variant)

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
     * `ProfileRepositoryImpl` reads those preferences once, from its `init` block — which
     * The graph is built by the time any test code executes, so a profile seeded
     * into preferences here is never read and the store stays empty. Going through `add` also
     * persists it, so the result is the same state the profile editor would leave behind.
     * [ProfileRepository.newDraft] names it (`LocalProfile1`) and zeroes every block, which is
     * deliberately invalid — fill them in or the profile switch below is rejected as invalid.
     */
    private fun seedLocalProfile() {
        val profile = profileRepository.newDraft().copy(
            mgdl = true,
            ic = singleBlock(10.0),
            isf = singleBlock(50.0),
            basal = singleBlock(0.5),
            target = singleTargetBlock(100.0, 110.0)
        )
        check(profile.name == PROFILE_NAME) { "Expected the draft to be named $PROFILE_NAME, got ${profile.name}" }
        runBlocking { profileRepository.add(profile) }.getOrThrow()
    }

    /**
     * Activates the profile [seedLocalProfile] added, the way the profile UI does.
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

        // Mirrors ActionProfileSwitch: an indefinite 100% switch to the seeded profile. This is the FIRST
        // switch on a fresh install, so nothing is in force yet and there is no "current insulin" to inherit
        // — the seeded catalogue entry is picked explicitly, which is what the user does when filling.
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
                iCfg = insulinManager.insulins.first()
            )
        }
        checkNotNull(switch) {
            "Could not activate the seeded local profile '$PROFILE_NAME' — store offers ${store.getProfileList()}"
        }
    }

    /** The profile-editor JSON shape: a single all-day value. */
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

    /**
     * Reads pump status through the command queue until the pump reports initialized.
     * The overview can't bootstrap this itself: Refresh/Pump history/User options are all
     * `visible = isInitialized` (DanaOverviewViewModel), and the bottom-bar button that got us onto
     * this screen only shows while the pump is *not* initialized — so the first read has to come
     * from outside the UI. changePump() does exactly what the app does on a device change: queue a
     * readStatus. Once it completes against the emulator the actions appear.
     */
    protected fun initializePumpFromUi() {
        if (!awaitTrue(INIT_PUMP_TIMEOUT) {
                // Only queue a read when the last one has finished. On Dana RS changePump() resets
                // DanaPump before it queues the read, so a fresh one each poll would stack a backlog of
                // reads that keep resetting the pump and churning the action list long after this
                // returns — which is what made the later steps flaky.
                if (queueIdle()) requestStatusRead()
                waitForVisible("Pump history", 2_000)
            }
        ) error("Pump never reported initialized — 'Pump history' never appeared")
        waitForQueueIdle()
    }

    /** Reads pump status through the command queue until the pump reports initialized, with no screen. */
    protected fun initializePumpThroughQueue() {
        if (!awaitTrue(INIT_PUMP_TIMEOUT) {
                if (queueIdle()) requestStatusRead()
                activePlugin.activePump.isInitialized()
            }
        ) error("The pump never reported initialized")
        waitForQueueIdle()
    }

    /**
     * True when no command is queued or running.
     * The one deterministic "the overview has settled" signal: `isInitialized` is stable-true and
     * every action present exactly while nothing is in flight. A read after a `danaPump.reset()` (a
     * pump change) has `isInitialized` false and the whole action list gone until it completes —
     * tapping into that window is what mis-fired onto Unpair and timed out on absent buttons.
     */
    // With the app-owned CommandExecutor, an empty queue with nothing performing is a sufficient idle
    // signal. The old "is the WorkManager worker still running its disconnect tail?" check existed only
    // to avoid a stranded command: the dying worker never re-polled the queue and
    // notifyAboutNewCommand skipped scheduling a new one while it was still RUNNING. The single
    // long-lived loop removes that race — signal() buffers a conflated wake it consumes on the next
    // receive(), re-draining instead of stranding — so the worker-liveness check is gone.
    protected fun queueIdle() = commandQueue.size() == 0 && commandQueue.performing() == null

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
        // "Close sheet" is Material3's own description for the scrim - the dim strip above the sheet -
        // so finding it means a modal bottom sheet is up. Do NOT tap it: the tap lands on the scrim's
        // centre, and once the sheet is tall enough (a small screen, or one permission row more) that
        // centre is on the sheet itself and nothing is dismissed. Adding a row to the permissions sheet
        // broke every UI test this way. Back dismisses a modal sheet whatever its height. It is guarded
        // by the find, so on a screen with no sheet Back is never pressed and cannot walk the wizard back.
        //
        // The sheet animates in/out, so the node can invalidate between find and press — swallow the
        // stale hit and let waitForOverview's loop retry rather than fail the whole test on it. (Seen
        // only under package-run load; harmless in isolation.)
        runCatching { if (device.findObject(byDesc("Close sheet")) != null) device.pressBack() }
    }

    /**
     * Overview → Manage → Pump management, which renders the active pump's compose content
     * (`ComposeMainActivity.handlePluginClick`) — the Dana overview screen, reachable because the
     * subclass's `seedPairedPump` makes this Dana the active pump.
     * Deliberately **not** the "Dana-i/RS" button in the bottom bar, which is the more obvious
     * route: `ComposeMainActivity` only offers that one while the pump is *not* initialized
     * (`showPumpSetup`), and the pump initializes itself moments after the activity launches. Which
     * of the two wins is a race the test cannot control — it lost locally (button never rendered)
     * and won in CI (build 40254), on identical code. Manage → Pump management has no such gate, and
     * is also the route a user with a working pump actually takes.
     * Not the nav drawer either: that is a fixed menu (history/statistics/maintenance/...) with no
     * plugin entries.
     */
    protected fun openDanaPlugin() {
        openVia("Manage", expect = PUMP_MANAGEMENT)
        openVia(PUMP_MANAGEMENT, expect = "Unpair")
    }

    /** The last [lines] lines of this app's own log that match [pattern], without the queue's waiting lines. */
    protected fun appLog(pattern: Regex, lines: Int = APP_LOG_LINES): String = runCatching {
        Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", APP_LOG_READ.toString())).inputStream.bufferedReader()
            .readLines().filter { pattern.containsMatchIn(it) && !QUEUE_WAITING.containsMatchIn(it) }.takeLast(lines).joinToString("\n")
    }.getOrElse { "The log could not be read: ${it.message}" }

    protected fun assertVisible(label: String, timeout: Long = STEP_TIMEOUT) {
        find(label, timeout)
    }

    // ---- ui helpers (same contract as SetupWizardE2ETest) -----------------------------------

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

    /**
     * Like [byDesc], but matches any content description that *contains* [s].
     *
     * A [Pattern] in a [BySelector] is a whole-string match, and the number steppers are no longer
     * described by a fixed English word: their description is localized and carries the field label
     * and the step, e.g. "increment Temp basal by 10" (`a11y_plus_button_description`). So [DECREMENT]
     * and [INCREMENT] can only be matched as a part of the description. [byDesc] stays exact — the
     * other call sites rely on that.
     */
    protected fun byDescContains(s: String): BySelector =
        By.desc(Pattern.compile(".*" + Pattern.quote(s) + ".*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL))

    protected fun find(label: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    /**
     * Taps [label] by coordinates rather than through the node.
     *
     * `UiObject2.click` re-reads the accessibility node when it runs, so the node has to survive the whole
     * gesture. On a screen that recomposes continuously - the Dana overview updates as the pump talks - it
     * often does not, and every attempt throws StaleObjectException. Reading the bounds of a node from [find]
     * has the same gap: in CI it went stale on every one of ten tries (DanaRsEmulatorUiTest dying in
     * returnToDanaOverview). So the node is found and its bounds are read in one call ([boundsOf]). Once the
     * Rect is in hand the tap cannot go stale.
     */
    protected fun click(label: String) {
        val bounds = boundsOf(label)
        device.click(bounds.centerX(), bounds.centerY())
    }

    /**
     * The bounds of [label], matched by text or content-desc like [find], but through the older `UiObject`: it
     * looks the node up at the moment it is asked, so there is no handle that can go stale.
     */
    private fun boundsOf(label: String, timeout: Long = STEP_TIMEOUT): Rect {
        val whole = "(?i)" + Pattern.quote(label)
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            for (selector in listOf(UiSelector().textMatches(whole), UiSelector().descriptionMatches(whole))) {
                val node = device.findObject(selector)
                // exists() does not wait, so a label that is only a content-desc does not wait out the text lookup.
                if (node.exists()) try {
                    return node.visibleBounds
                } catch (_: UiObjectNotFoundException) {
                    // Gone between the two calls: look again.
                }
            }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for '$label'")
    }

    /**
     * Opens the Manage sheet and taps [action], scrolling the sheet to reach it.
     * Temp basal / Extended bolus sit near the bottom of `ManageBottomSheet`, below the fold, so a
     * plain find never sees them — the sheet has to be scrolled first.
     */
    protected fun openManageAction(action: String) {
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

    /** [openVia] for a screen recognised by a content description that only *contains* [expect] (see [byDescContains]). */
    protected fun openViaDescContains(open: String, expect: String, attempts: Int = 4) {
        repeat(attempts) {
            click(open)
            if (waitForDescContains(expect)) return
        }
        error("no description containing '$expect' after $attempts taps on '$open'")
    }

    protected fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    /** [waitForVisible] for a content description that only *contains* [desc] (see [byDescContains]). */
    protected fun waitForDescContains(desc: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byDescContains(desc)) != null) return true
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

    private fun clearAllSharedPrefs() = clearAppSettings(instrumentation.targetContext)

    companion object {

        const val PKG = "info.nightscout.androidaps"

        /** `core.ui.R.string.pump_management` — the Manage sheet's entry onto the active pump. */
        private const val PUMP_MANAGEMENT = "Pump"

        // The first word of every number stepper's description (`core.ui.R.string.increment` /
        // `decrement`, and the start of `a11y_plus_button_description` / `a11y_min_button_description`).
        // Only usable with byDescContains — see there. Non-private: subclasses tap steppers too.
        const val INCREMENT = "increment"
        const val DECREMENT = "decrement"

        private const val PROFILE_NAME = "LocalProfile1"

        /**
         * The first app start of a shard comes while the other emulators and Gradle load the CI box: as `ScreenDriver`
         * waits, 2 minutes. With 1 minute the first test of shard A timed out on the splash (CircleCI 42443).
         */
        private const val INIT_TIMEOUT = 120_000L
        private const val STEP_TIMEOUT = 30_000L
        // Non-private: referenced from subclass legs and inlined into subclasses via withStaleRetry.
        const val IDLE_MS = 300L
        const val STALE_RETRIES = 10
        const val STALE_SETTLE_MS = 700L

        private const val INIT_PUMP_TIMEOUT = 60_000L
        private const val QUEUE_IDLE_TIMEOUT = 60_000L
        private const val PROFILE_STORE_TIMEOUT = 20_000L
        private const val OPEN_ATTEMPTS = 3

        private const val POLL_MS = 250L

        private val QUEUE_WAITING = Regex("waiting for disconnect|connecting \\d")
        private const val APP_LOG_LINES = 60
        private const val APP_LOG_READ = 5000
    }
}
