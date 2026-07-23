package app.aaps.e2e

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.plugins.constraints.objectives.ObjectivesPlugin
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
 * **In-process** end-to-end UI test: drives a fresh AAPS setup wizard all the way to a running,
 * open-loop app and exercises the core treatments:
 *
 *  fresh start → EULA → master password → units → patient safety → BG source (default) →
 *  create + activate a profile → Virtual Pump → OpenAPS SMB → Sensitivity Oref1 → start objective 1 →
 *  FINISH → manual bolus (assert IOB + DB) → carbs (assert DB) → temp target (assert chip + DB) →
 *  enable Open Loop (assert mode + DB).
 *
 * ## Why this lives in `:app/androidTest` (vs the standalone `:e2e` module)
 * Running here, the test executes **in the app process** under the Hilt test application. That has two
 * payoffs the black-box `:e2e` module can't give: it **produces app code coverage** (collected from the
 * app process by jacoco) and it is **runnable from the Studio gutter**. It rides the existing
 * `:app:connectedFullDebugAndroidTest` in CI with no extra config.
 *
 * The cost is that the Hilt test app doesn't run `MainApp.onCreate`, so [setUp] reproduces the minimum
 * the UI needs: register the plugins, flip the init-complete gate [ComposeMainActivity]'s splash reads,
 * and clear SharedPreferences for a fresh wizard (there is no `pm clear` in-process — it would kill the
 * test). [Config] is `@Singleton` so the instance we flip is the one the activity observes.
 *
 * ## Why it is inherently fragile (read before editing)
 * Same as any full-wizard E2E: selectors match **case-insensitively against text OR content-desc**;
 * navigation is **verified-with-retry** ([tapNext]/[openVia]); profile number fields use **real key
 * events**; confirm buttons behind the IME are reached only after [hideKeyboard]. English-only.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetupWizardE2EHiltTest {

    val hiltRule = HiltAndroidRule(this)

    // RetryRule outermost: a flaky timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)

    // The plugin/config init that MainApp does in onCreate (the Hilt test app can't). Inlined rather
    // than inherited from HiltInstrumentedTest so SharedPreferences can be cleared BEFORE the graph
    // initializes (see setUp) — order matters for a clean fresh-app boot.
    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var pluginList: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var configBuilder: ConfigBuilder
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector
    @Inject lateinit var config: Config
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        // Clear SharedPreferences for a fresh wizard BEFORE the Hilt graph reads any prefs (there is no
        // pm clear in-process — it would kill the test). Doing it before hiltRule.inject() matters: every
        // singleton then initializes against empty prefs and seeds its defaults exactly like a fresh app
        // (e.g. InsulinImpl seeds a default insulin; clearing AFTER init left its list empty → AppContent
        // crashed in getICfg). Then reproduce the bits of MainApp.onCreate the UI needs.
        clearAllSharedPrefs()
        hiltRule.inject()
        preferences.put(BooleanNonKey.GeneralSetupWizardProcessed, false) // force the wizard to show
        pluginStore.plugins = pluginList
        configBuilder.initialize()
        config.initCompleted()                                            // flip splash gate → AppContent renders
        // Pre-seed objective 1 as started: the wizard gates its FINISH button on
        // objectives[FIRST].isStarted, and the UI "Start" runs an NTP network-time check that can't
        // complete under the Hilt test app (no connectivity wiring from MainApp.onCreate).
        objectivesPlugin.objectives.firstOrNull()?.startedOn = System.currentTimeMillis()
        // Heads-up banners (e.g. the profile-switch "loop disabled" toast) must not cover the UI.
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        // Start logcat fresh so the DB-insert assertions only match THIS run's records.
        device.executeShellCommand("logcat -c")
        // Grant notifications up-front so the wizard's permission step is a no-op.
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS")
        }
    }

    @After
    fun tearDown() {
        // Don't poison sibling instrumented tests with the wizard config we just wrote.
        clearAllSharedPrefs()
    }

    @Test
    fun full_setup_treatments_and_management_screens() {
        val scenario = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            walkthrough()
        } catch (t: Throwable) {
            logScreen("E2E_SCREEN") // capture the LIVE failing screen before the activity is torn down
            throw t
        } finally {
            scenario.close()
        }
    }

    private fun walkthrough() {
            waitForWizard()
            completeSetupWizard()
            assertVisible("LocalProfile1") // active profile chip proves the wizard finished → on overview

            deliverManualBolus(units = "1.0", confirmButtonLabel = "1.00 U")
            // IOB on the overview reflects the just-delivered bolus. The recompute can lag a few
            // seconds behind delivery, so allow a generous window (the value stays "1.00 U" within it).
            assertVisible("1.00 U", timeout = 40_000)               // UI: IOB on the overview
            assertDbInsert("Bolus", "amount=1\\.0,")                // DB: the bolus row was persisted

            deliverCarbs(grams = "20", confirmButtonLabel = "20 g")
            // Returning to the overview (active-profile chip present) confirms the carb dialog flow
            // completed; COB on the overview lags a full calc cycle, so it isn't asserted directly here.
            assertVisible("LocalProfile1")                          // UI: back on the overview
            assertDbInsert("Carbs", "amount=20\\.0,")               // DB: the carbs row was persisted

            activateTempTarget()
            // The overview target chip shows the active temp target as "100 (<minutes>')" — the "(" /
            // countdown is what distinguishes an active temp target from the plain profile target.
            assertTextContains("100 (")                             // UI: target chip
            assertDbInsert("TemporaryTarget")                       // DB: the temp target was persisted

            // ---- additional screens/dialogs: coverage for management surfaces not on the core path ----
            // Done BEFORE enabling the loop, on purpose: once the loop runs, the APS recomputes and churns
            // the overview, and on the slow CI emulator the "Manage"/quick-launch buttons couldn't be caught
            // in a stable frame within the find timeout. With the loop still off the overview is quiet.
            returnToOverview()            // settle on a clean overview before navigating away
            visitInsulinManagement()      // Manage → Insulin (InsulinManagementScreen/ViewModel/Carousel)
            visitProfileManagement()      // Manage → Profile (profile management screen)
            openAndCancelBolusWizard()    // Treatments → Bolus wizard → add carbs → CANCEL (edge: no delivery)
            visitPreferences()            // toolbar Settings → preferences screen + expand a category
            visitStatistics()             // drawer → Statistics (StatsScreen/ViewModel)
            visitHistoryBrowser()         // drawer → History browser (treatment history list)
            visitScenes()                 // Manage → Scenes: create via wizard, run, then end it

            // Enable Open Loop LAST — after this the loop churns the overview, so do no more navigation.
            enableOpenLoop()
            // The chip now reflects open-loop mode ("Open Loop" / "Open loop"); match leniently.
            assertContains("Open Loop")                             // UI: loop-mode chip
            assertDbInsert("RunningMode", "OPEN_LOOP")              // DB: the running mode was persisted
    }

    // ---- wizard -------------------------------------------------------------------------------

    /** Waits for the wizard to be reachable, dismissing the permissions sheet that covers Next. */
    private fun waitForWizard() {
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            dismissBlockingSheetIfPresent()
            if (device.findObject(byText("Next")) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Wizard 'Next' never appeared within ${INIT_TIMEOUT}ms — UI stuck on splash or wizard not shown")
    }

    private fun completeSetupWizard() {
        dismissBlockingSheetIfPresent()

        // Each step advances with tapNext(<title of the NEXT screen>) so a tap that fires mid-
        // transition (Next enabled but the step not yet ready) is retried until it actually advances.

        // 1. Welcome → 2. EULA
        tapNext("End User License Agreement")

        // 2. EULA — scroll the disclaimer to reveal the accept control, then continue.
        scrollTo("I understand and agree")
        click("I understand and agree")
        tapNext("Permissions")

        // 3. Permissions — notifications already granted, AAPS directory intentionally skipped.
        dismissBlockingSheetIfPresent()
        tapNext("Master password")

        // 4. Master password
        setMasterPassword("aaps")
        tapNext("Units")

        // 5. Units
        click("mg/dL")
        tapNext("Display Settings")

        // 6. Display settings (defaults are valid)
        tapNext("Communication")

        // 7. Communication / sync plugins — skip (self-contained, no Nightscout)
        tapNext("Client control")

        // 7b. Client control (master ↔ client pairing) — skip
        tapNext("Name")

        // 8. Patient name — skip
        tapNext("Patient type")

        // 9. Patient type / safety limits
        click("Adult")
        tapNext("Insulin")

        // 10. Insulin (a default type is pre-selected)
        tapNext("BG Source")

        // 11. BG source — leave the default source. The test's assertions don't depend on BG flowing,
        // so the engineering-mode-gated Random BG source is intentionally not selected.
        tapNext("Profile")

        // 12. Profile — create one with valid values. The step has both a title and a control
        // labelled "Profile"; the control is the lower one (and renders a beat after the title).
        openVia("Profile", expect = "Add new profile", lowest = true)
        createLocalProfile()
        device.pressBack() // profile manager → wizard
        tapNext("Profile switch")

        // 13. Profile switch — activate the profile we just created
        openVia("Do Profile Switch", expect = "Activate")
        clickLowest("Activate")       // the bottom action button, not the screen title
        click("OK")                   // confirmation
        assertDbInsert("ProfileSwitch") // DB: the profile switch was persisted
        // The switch posts a "loop disabled" heads-up that can cover the top-left Close button, so
        // leave the Activate screen with Back (coordinate-independent) instead of tapping Close.
        pressBackUntil("Next")        // activate screen → wizard
        tapNext("Pump")

        // 14. Pump — Virtual Pump is the default selection
        tapNext("APS")

        // 15. APS — OpenAPS SMB is the default selection
        tapNext("Sensitivity detection")

        // 16. Sensitivity — Sensitivity Oref1 is the default selection
        tapNext("Objectives")

        // 17. Objectives — objective 1 was pre-seeded as started in setUp(), so the wizard's final
        // step offers FINISH directly (the UI "Start" needs an NTP check that can't run in-process).
        openVia("FINISH", expect = "LocalProfile1")  // wizard → overview (active profile chip)
    }

    /** Master-password sub-flow: Set → Protection screen → enter + confirm → back to wizard. */
    private fun setMasterPassword(password: String) {
        click("Set")
        clickUntilVisible("Master password", EDIT_TEXT) // opening the dialog is occasionally flaky
        setEditText(0, password, verify = false)       // password renders as bullets → can't read back
        setEditText(1, password, verify = false)
        hideKeyboard()                                 // OK sits behind the IME; click() taps coordinates
        click("OK")
        click("Back")                                  // Protection screen → wizard
    }

    /** Fills the four profile tabs with valid values and saves. */
    private fun createLocalProfile() {
        openVia("Add new profile", expect = "ISF") // open the editor, retrying until its tabs render

        setEditText(0, "10")          // IC tab (single value field)
        click("ISF"); setEditText(0, "50")
        click("BAS"); setEditText(0, "0.5")
        click("TARG")
        setEditText(0, "100")         // Low
        setEditText(1, "110")         // High

        click("Save")
        click("Close")                // editor → profile manager
    }

    // ---- treatments ---------------------------------------------------------------------------

    private fun deliverManualBolus(units: String, confirmButtonLabel: String) {
        openVia("Treatments", expect = "Insulin")      // overview quick-launch → action sheet
        openVia("Insulin", expect = "+$units")         // "Deliver bolus manually" → bolus dialog
        click("+$units")                               // quick-add (e.g. +1.0)
        openVia(confirmButtonLabel, expect = "OK")     // confirm button (e.g. "1.00 U") → confirmation
        click("OK")                                    // "Bolus: 1.0 U" confirmation
    }

    private fun deliverCarbs(grams: String, confirmButtonLabel: String) {
        openVia("Treatments", expect = "Carbs")        // overview quick-launch → action sheet
        openVia("Carbs", expect = "+$grams")           // "Record carbs without insulin" → carbs dialog
        click("+$grams")                               // quick-add (e.g. +20)
        openVia(confirmButtonLabel, expect = "OK")     // confirm button (e.g. "20 g") → confirmation
        click("OK")                                    // "Carbs: 20 g" confirmation
    }

    private fun activateTempTarget() {
        openVia("Manage", expect = "Temp Target")      // overview quick-launch → manage sheet
        openVia("Temp Target", expect = "Activate")    // → temp-target screen
        // The real app seeds default presets (Eating Soon = 90 …) on first run; the Hilt test app
        // doesn't, so the screen has no presets and "Activate" applies a temp target at the profile
        // target (100, set in createLocalProfile) instead.
        openVia("Activate", expect = "OK")             // activate → confirmation
        click("OK")
    }

    private fun enableOpenLoop() {
        // The loop status chip's label varies with BG state ("Disabled loop", "Loop Disabled", …),
        // so open the running-mode menu by matching any chip containing "loop".
        openContains("loop", expect = "Open Loop")     // overview loop status → running-mode menu
        openVia("Open Loop", expect = "OK")            // → "Running mode: Open Loop" confirmation
        click("OK")
    }

    // ---- additional screens / dialogs (exercise management surfaces off the core treatment path) ----

    /** Manage → Insulin: the insulin-management screen (carousel + nickname/peak/DIA editors). */
    private fun visitInsulinManagement() {
        openVia("Manage", expect = "Site Rotation")    // open the Manage sheet (distinctive marker)
        openVia("Insulin", expect = "Add new insulin") // → insulin management screen
        returnToOverview()
    }

    /** Manage → Profile: the profile-management screen (clone/activate/edit the active profile). */
    private fun visitProfileManagement() {
        openVia("Manage", expect = "Site Rotation")
        openVia("Profile", expect = "Clone")           // → profile management screen
        returnToOverview()
    }

    /**
     * Manage → Scenes: creates a scene through the wizard (from the "Hot Weather" template — a single
     * profile-switch action, no picker input, auto-named), runs it, then ends it. Covers the whole
     * `ui/compose/scenes/` surface: SceneListScreen, the wizard steps, and the activate/deactivate dialogs.
     *
     * The template gives a valid, activatable scene with no per-step input, so the wizard's action steps
     * (profile/TT/SMB/loop/careportal/duration/chain) advance on a plain "Next"; the final NAME_ICON step
     * has the pre-filled name so "Finish" is enabled.
     */
    private fun visitScenes() {
        openVia("Manage", expect = "Site Rotation")
        openVia("Scenes", expect = "Scene")                 // Manage → Scenes → SceneList (its "+" FAB, desc "Scene")
        click("Scene")                                       // Add FAB (content-desc) → wizard
        assertVisible("Start from template")
        click("Sick Day")                                    // template (profile 150% only — no temp-target
                                                             // step to dead-end); prefills the scene, INFO step

        // Each action step advances on "Next"; walk to the final NAME_ICON step (which shows "Finish").
        var guard = 0
        while (!waitForVisible("Finish", 1_500) && guard++ < 12) {
            click("Next"); device.waitForIdle(IDLE_MS)
        }
        click("Finish")                                      // save() → back on SceneList
        assertVisible("Sick Day")                            // the created scene row

        // Run it: the card's Activate (Play) icon → confirmation dialog → Activate.
        click("Activate")                                    // Play IconButton (content-desc)
        assertTextContains("Activate scene")                 // SceneActivationDialog title
        click("Activate")                                    // dialog confirm button (text)
        assertVisible("End Scene")                           // card now active → Stop icon shown

        // End it: the card's End-Scene (Stop) icon → confirmation dialog → End Scene (reverts changes).
        click("End Scene")                                   // Stop IconButton (content-desc)
        assertTextContains("End scene")                      // SceneDeactivationDialog title
        click("End Scene")                                   // dialog confirm button (text)
        returnToOverview()
    }

    /** Treatments → Bolus wizard: open the calculator, add a quick-carb preset, then CANCEL (no delivery). */
    private fun openAndCancelBolusWizard() {
        openVia("Treatments", expect = "Bolus wizard")
        openVia("Bolus wizard", expect = "Correction") // → wizard dialog (calculator)
        click("CAKE")                                  // a quick-carb preset → exercises the calculation
        device.waitForIdle(IDLE_MS)
        click("Close")                                 // dismiss WITHOUT delivering — the cancel path
        returnToOverview()
    }

    /**
     * Toolbar Settings → the preferences screen, then sweep top-to-bottom expanding every category and
     * scrolling — this renders all the per-plugin preference controls, the single largest UI surface.
     */
    private fun visitPreferences() {
        openVia("Settings", expect = "Protection")     // toolbar Settings (desc) → all-preferences screen
        repeat(PREF_SWEEPS) {
            runCatching { device.findObject(byDesc("Expand"))?.click() } // expand the topmost collapsed category
            device.waitForIdle(IDLE_MS)
            runCatching { device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f) } // reveal the next
            device.waitForIdle(IDLE_MS)
        }
        returnToOverview()
    }

    /** Nav drawer → Statistics (StatsScreen/ViewModel). */
    private fun visitStatistics() {
        click("Open navigation"); device.waitForIdle(IDLE_MS) // open the nav drawer
        openVia("Statistics", expect = "Back")                // → statistics screen
        returnToOverview()
    }

    /** Nav drawer → History browser (treatment-history list). */
    private fun visitHistoryBrowser() {
        click("Open navigation"); device.waitForIdle(IDLE_MS)
        openVia("History browser", expect = "Back")           // → history browser
        returnToOverview()
    }

    // ---- assertions ---------------------------------------------------------------------------

    private fun assertVisible(label: String, timeout: Long = STEP_TIMEOUT) {
        // find() polls text OR content-desc and throws a descriptive error if absent.
        find(label, timeout)
    }

    /** Asserts some element's text contains [substring] (for values with a changing suffix, e.g. a countdown). */
    private fun assertTextContains(substring: String, timeout: Long = STEP_TIMEOUT) {
        checkNotNull(device.wait(Until.findObject(By.textContains(substring)), timeout)) {
            "No element containing text '$substring' within ${timeout}ms"
        }
    }

    /**
     * Verifies a row was actually persisted, by asserting the persistence layer's insert log line
     * (`Inserted <entity> …`, optionally also matching [valueFragment] on the same line). The app DB
     * isn't directly queryable here (no on-device `sqlite3`), so this confirms the Room insert ran —
     * which is what writes the row. logcat is cleared in [bootRealUi], so only this run's inserts match.
     */
    private fun assertDbInsert(entity: String, valueFragment: String? = null, timeout: Long = STEP_TIMEOUT) {
        val regex = "Inserted $entity\\b" + (valueFragment?.let { ".*$it" }.orEmpty())
        val pattern = Pattern.compile(regex)
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (pattern.matcher(device.executeShellCommand("logcat -d -s DATABASE:D")).find()) return
            device.waitForIdle(IDLE_MS)
        }
        error("No DB insert matching /$regex/ in logcat within ${timeout}ms — '$entity' was not persisted")
    }

    // ---- ui helpers ---------------------------------------------------------------------------

    /** Case-insensitive selector matching visible text. */
    private fun byText(s: String): BySelector =
        By.text(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

    /** Case-insensitive selector matching content-description. */
    private fun byDesc(s: String): BySelector =
        By.desc(Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE))

    /** Finds an element by text or content-desc, polling until [timeout]; throws on miss. */
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

    /** Finds an element whose text or content-desc *contains* [substring] (case-insensitive); throws on miss. */
    private fun findContains(substring: String, timeout: Long = STEP_TIMEOUT): UiObject2 {
        val text = By.text(Pattern.compile(".*${Pattern.quote(substring)}.*", Pattern.CASE_INSENSITIVE))
        val desc = By.desc(Pattern.compile(".*${Pattern.quote(substring)}.*", Pattern.CASE_INSENSITIVE))
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            device.findObject(text)?.let { return it }
            device.findObject(desc)?.let { return it }
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for an element containing '$substring'")
    }

    /** Like [openVia] but clicks an element whose text/desc *contains* [contains] (for labels that vary). */
    private fun openContains(contains: String, expect: String, attempts: Int = 4) {
        repeat(attempts) {
            withStaleRetry { findContains(contains).click() }
            if (waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on an element containing '$contains'")
    }

    /** Asserts an element whose text/desc contains [substring] is present. */
    private fun assertContains(substring: String, timeout: Long = STEP_TIMEOUT) {
        findContains(substring, timeout)
    }

    /** Clicks [label], retrying until [appears] is visible — for occasionally-flaky open transitions. */
    private fun clickUntilVisible(label: String, appears: BySelector, attempts: Int = 3) {
        repeat(attempts) {
            runCatching { click(label) } // tolerate a stale-exhausted click; the loop retries the open
            if (device.wait(Until.findObject(appears), STEP_TIMEOUT) != null) return
        }
        error("'$appears' never appeared after $attempts clicks on '$label'")
    }

    /**
     * Taps the wizard's Next button and waits for [nextScreenMarker] (a label unique to the next
     * step). Retries the tap if it didn't take — the wizard occasionally enables Next a moment
     * before the step is ready, so a tap can be a no-op.
     */
    private fun tapNext(nextScreenMarker: String, attempts: Int = 4) {
        repeat(attempts) {
            click("Next")
            if (waitForVisible(nextScreenMarker)) return
        }
        error("Wizard did not advance to '$nextScreenMarker' after $attempts Next taps")
    }

    /** Presses Back until [label] is visible — leaves a sub-screen and closes the shade if it slipped open. */
    private fun pressBackUntil(label: String, attempts: Int = 4) {
        repeat(attempts) {
            if (waitForVisible(label, 1500)) return
            device.pressBack()
            device.waitForIdle(IDLE_MS)
        }
        if (!waitForVisible(label, STEP_TIMEOUT)) error("'$label' not visible after $attempts Back presses")
    }

    /** Taps [open] (optionally the lowest match) and waits for [expect]; retries if the screen didn't open. */
    private fun openVia(open: String, expect: String, lowest: Boolean = false, attempts: Int = 4) {
        repeat(attempts) {
            if (lowest) clickLowest(open) else click(open)
            if (waitForVisible(expect)) return
        }
        error("'$expect' not visible after $attempts taps on '$open'")
    }

    /** True if an element with [label] as text or content-desc appears within [timeout]. */
    private fun waitForVisible(label: String, timeout: Long = STEP_TIMEOUT): Boolean {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null || device.findObject(byDesc(label)) != null) return true
            device.waitForIdle(IDLE_MS)
        }
        return false
    }

    /** Clicks the lowest on-screen element with the given label (disambiguates title vs action button). */
    private fun clickLowest(label: String) = withStaleRetry {
        find(label) // ensure present
        val matches = device.findObjects(byText(label)) + device.findObjects(byDesc(label))
        (matches.maxByOrNull { it.visibleBounds.centerY() } ?: error("'$label' not found"))
            .click()
    }

    /**
     * Re-runs [block] when a node goes stale mid-interaction. Compose recomposes frequently (e.g.
     * the IME animating in, or a screen still transitioning), invalidating the AccessibilityNodeInfo
     * between find and click; each retry re-finds a fresh node. Slow CI emulators (headless API-31
     * swiftshader) recompose for longer, so this retries generously and settles between attempts.
     */
    private inline fun withStaleRetry(times: Int = STALE_RETRIES, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block()
                return
            } catch (e: StaleObjectException) {
                last = e
                device.waitForIdle(STALE_SETTLE_MS) // let the recomposing screen settle before re-finding
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted after $times attempts (node kept going stale)")
    }

    /** Scrolls the first scrollable container down until [label] appears, then returns it. */
    private fun scrollTo(label: String): UiObject2 {
        repeat(MAX_SCROLLS) {
            device.findObject(byText(label))?.let { return it }
            device.findObject(byDesc(label))?.let { return it }
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                scrollable.scroll(Direction.DOWN, 0.8f)
            } else {
                val w = device.displayWidth / 2
                device.swipe(w, (device.displayHeight * 0.75).toInt(), w, (device.displayHeight * 0.25).toInt(), 20)
            }
            device.waitForIdle(IDLE_MS)
        }
        return find(label) // last attempt; throws if still absent
    }

    /**
     * Types [value] into the [index]-th EditText via real key events. The profile editor's custom
     * number fields ignore the accessibility set-text action (it changes the visible text but not
     * the field's state → validation fails), so we focus the field and inject actual keystrokes,
     * which fire `onValueChange`. Afterwards the IME is dismissed (it is reliably up after typing),
     * leaving the next field/button unobscured for the following tap.
     */
    private fun setEditText(index: Int, value: String, verify: Boolean = true) {
        repeat(SET_TEXT_ATTEMPTS) {
            withStaleRetry {
                waitForEditTexts(index + 1)[index].click() // focus the field (raises the IME)
                device.waitForIdle(IDLE_MS)
                device.executeShellCommand("input keyevent 123")                    // MOVE_END
                device.executeShellCommand("input keyevent 67 67 67 67 67 67 67 67") // clear (8× DEL)
                device.executeShellCommand("input text $value")                     // real keystrokes
                device.waitForIdle(IDLE_MS)
                // Dismiss the IME so the next field isn't behind it — but ONLY if it's actually
                // showing. An unconditional Back here would navigate *out of the editor* when no
                // keyboard is up (a device/timing-dependent failure that empties the screen).
                hideKeyboard()
            }
            // Masked fields (the master password) render as bullets, so the value can't be read back
            // — type once. Visible profile fields ARE confirmed: if the field never focused (input
            // went nowhere) the value won't be set, so re-type rather than leave it invalid.
            if (!verify) return
            val current = runCatching { device.findObjects(EDIT_TEXT).getOrNull(index)?.text }.getOrNull()
            if (current == value) return
            device.waitForIdle(IDLE_MS)
        }
        // Proceed even if unconfirmed; the editor's own validation (Save stays hidden) surfaces it.
    }

    private fun waitForEditTexts(count: Int): List<UiObject2> {
        val end = SystemClock.uptimeMillis() + STEP_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            val fields = device.findObjects(EDIT_TEXT)
            if (fields.size >= count) return fields
            device.waitForIdle(IDLE_MS)
        }
        error("Expected at least $count EditText fields")
    }

    /** AAPS re-shows a "Permissions required" bottom sheet on resume until the directory is granted. */
    private fun dismissBlockingSheetIfPresent() {
        device.findObject(byDesc("Close sheet"))?.click()
    }

    /**
     * Hides the soft keyboard if one is showing. [UiObject2.click] taps the node's centre
     * *coordinate*, so a confirm button sitting behind the IME would otherwise receive the tap.
     * Back dismisses the IME before it would close the dialog; no-op when no keyboard is up.
     */
    private fun hideKeyboard() {
        val imeShowing = device.findObject(By.pkg(Pattern.compile(".*inputmethod.*"))) != null
        if (imeShowing) {
            device.pressBack()
            device.waitForIdle(IDLE_MS)
        }
    }

    /** Returns to the overview from any sub-screen/bottom sheet: close a sheet, press Back, until it shows. */
    private fun returnToOverview() {
        repeat(6) {
            // "Open navigation" is unique to the overview toolbar (sub-screens show "Back"). Don't match
            // on "LocalProfile1" — the profile/insulin management screens display it too.
            if (waitForVisible("Open navigation", 1200)) return
            dismissBlockingSheetIfPresent()
            device.pressBack()
            device.waitForIdle(IDLE_MS)
        }
        assertVisible("Open navigation")
    }

    /** Logs every on-screen text/content-desc under [tag] (chunked; logcat truncates long lines). */
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

    /** Wipes all SharedPreferences for a fresh wizard, since `pm clear` would kill the in-process test. */
    private fun clearAllSharedPrefs() {
        val ctx = instrumentation.targetContext
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            if (f.name.endsWith(".xml"))
                ctx.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val INIT_TIMEOUT = 60_000L  // splash → wizard (init already flipped, so usually fast)
        private const val STEP_TIMEOUT = 30_000L
        private const val IDLE_MS = 300L
        private const val MAX_SCROLLS = 12
        private const val SET_TEXT_ATTEMPTS = 3
        private const val STALE_RETRIES = 10        // generous: slow CI emulators recompose for longer
        private const val STALE_SETTLE_MS = 700L    // wait for the screen to settle between stale retries
        private const val PREF_SWEEPS = 14          // expand+scroll passes over the preference categories
        private val EDIT_TEXT: BySelector = By.clazz("android.widget.EditText")
    }
}
