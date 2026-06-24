package app.aaps.e2e

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * Black-box end-to-end UI test that drives a **fresh** AAPS install all the way from the setup
 * wizard to a running, open-loop app and exercises the core treatment actions:
 *
 *  fresh start → EULA → master password → units → patient safety → BG source (default) →
 *  create + activate a profile → Virtual Pump → OpenAPS SMB → Sensitivity Oref1 → start objective 1 →
 *  FINISH → manual bolus (assert IOB) → carbs → activate a temp target (assert the target chip) →
 *  enable Open Loop (assert mode).
 *
 * ## How it runs (and why this is its own module)
 * This lives in the standalone `:e2e` (`com.android.test`) module, which instruments `:app` from its
 * own process. So the app under test runs as the **real production [app.aaps.MainApp]** — there is no
 * Hilt test-app swap (which would hang the splash) and no system-property gymnastics. Because the test
 * is a separate process, [setUp] can `pm clear` the app for guaranteed fresh wizard state. It is run by
 * the module's standard connected-test task, so **CI runs it with no manual steps**:
 * ```
 * ./gradlew.bat :e2e:connectedFullDebugAndroidTest      # builds :app:fullDebug + this, installs, runs
 * ```
 *
 * ## Why it is inherently fragile (read before editing)
 * A full-wizard E2E depends on screen text, scroll positions and timing. Selectors are matched
 * **case-insensitively against text OR content-description** to survive styling (e.g. all-caps
 * buttons); navigation is **verified-with-retry** ([tapNext]/[openVia]) because the wizard enables
 * Next a beat before a step is ready; profile number fields are filled with **real key events**
 * (the accessibility set-text action doesn't update their state); confirm buttons behind the IME are
 * reached only after [hideKeyboard]. Text is English-only — run on a device set to English. No
 * synthetic BG source is selected: none of the assertions depend on BG actually flowing, so the
 * engineering-mode-gated Random BG is intentionally skipped.
 */
@RunWith(AndroidJUnit4::class)
class SetupWizardE2ETest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    /** On failure, snapshot the window hierarchy to the test's files dir for post-mortem inspection. */
    private val dumpOnFailure: TestWatcher = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            runCatching {
                device.dumpWindowHierarchy(File(instrumentation.context.getExternalFilesDir(null), "e2e_fail.xml"))
            }
        }
    }

    /**
     * Hard safety net. Every individual wait in this test is already bounded by a timeout, but this
     * caps the whole run (incl. [setUp]) so an unforeseen hang fails the test and frees the device,
     * instead of blocking the CI job. Generous vs. the ~3 min normal run to avoid false failures on
     * slow CI emulators.
     */
    private val globalTimeout: Timeout = Timeout.seconds(600)

    // Timeout is the OUTER rule so it also bounds setUp(); dumpOnFailure snapshots the screen on failure.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(globalTimeout).around(dumpOnFailure)

    @Before
    fun setUp() {
        // Separate process → safe to wipe the app under test for a guaranteed-fresh wizard.
        device.executeShellCommand("pm clear $PKG")
        // Heads-up banners (e.g. the profile-switch "loop disabled" toast) must not cover the UI.
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        // Grant notifications up-front so the wizard's permission step is a no-op.
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(PKG, "android.permission.POST_NOTIFICATIONS")
        }
        launchApp()
    }

    @Test
    fun fresh_install_to_open_loop_with_core_treatments() {
        completeSetupWizard()
        assertVisible("LocalProfile1") // active profile chip proves the wizard finished → on overview

        deliverManualBolus(units = "1.0", confirmButtonLabel = "1.00 U")
        // IOB on the overview reflects the just-delivered bolus. The recompute can lag a few
        // seconds behind delivery, so allow a generous window (the value stays "1.00 U" within it).
        assertVisible("1.00 U", timeout = 40_000)

        deliverCarbs(grams = "20", confirmButtonLabel = "20 g")
        // Returning to the overview (active-profile chip present) confirms the carb dialog flow
        // completed; COB on the overview lags a full calc cycle, so it isn't asserted directly here.
        assertVisible("LocalProfile1")

        activateTempTarget()
        // The overview target chip switches from the profile range to the 90 mg/dL temp target,
        // shown as "90 (<minutes>')". Match the stable prefix since the countdown changes.
        assertTextContains("90 (")

        enableOpenLoop()
        // The chip now reflects open-loop mode ("Open Loop" / "Open loop"); match leniently.
        assertContains("Open Loop")
    }

    // ---- wizard -------------------------------------------------------------------------------

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

        // 17. Objectives — start objective 1, then finish
        openVia("Open Objectives", expect = "Start")
        click("Start")
        openVia("Back", expect = "FINISH")          // objectives list → wizard (in-app Back; system Back is a no-op here)
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
        click("Add new profile")

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
        openVia("Temp Target", expect = "Activate")    // → temp-target screen (pre-set "Eating Soon")
        openVia("Activate", expect = "OK")             // activate the preset → confirmation
        click("OK")                                    // "Target: 90.0 mg/dL ... Eating Soon"
    }

    private fun enableOpenLoop() {
        // The loop status chip's label varies with BG state ("Disabled loop", "Loop Disabled", …),
        // so open the running-mode menu by matching any chip containing "loop".
        openContains("loop", expect = "Open Loop")     // overview loop status → running-mode menu
        openVia("Open Loop", expect = "OK")            // → "Running mode: Open Loop" confirmation
        click("OK")
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

    // ---- ui helpers ---------------------------------------------------------------------------

    private fun launchApp() {
        // Launch the main activity explicitly via shell so a blind launch can't hit LeakCanary's
        // "Leaks" launcher activity that debug builds register under the same package.
        device.executeShellCommand("am start -n $PKG/$MAIN_ACTIVITY")
        device.wait(Until.hasObject(By.pkg(PKG).depth(0)), LAUNCH_TIMEOUT)
        // The app shows a heavy init splash, then the Welcome screen — usually UNDER a "Permissions
        // required" sheet that covers the Next button. Poll for Next while dismissing the sheet, so we
        // proceed the moment the wizard is reachable instead of waiting out the whole INIT_TIMEOUT
        // (a dead ~60s) for a Next that's hidden behind the sheet.
        val end = SystemClock.uptimeMillis() + INIT_TIMEOUT
        while (SystemClock.uptimeMillis() < end) {
            dismissBlockingSheetIfPresent()
            if (device.findObject(byText("Next")) != null) return
            device.waitForIdle(IDLE_MS)
        }
    }

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
            click(label)
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
     * the IME animating in), invalidating the AccessibilityNodeInfo between find and click; each
     * retry re-finds a fresh node.
     */
    private inline fun withStaleRetry(times: Int = 4, block: () -> Unit) {
        var last: StaleObjectException? = null
        repeat(times) {
            try {
                block()
                return
            } catch (e: StaleObjectException) {
                last = e
                device.waitForIdle(IDLE_MS)
            }
        }
        throw last ?: IllegalStateException("withStaleRetry exhausted without an error")
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

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val MAIN_ACTIVITY = "app.aaps.ComposeMainActivity"
        private const val LAUNCH_TIMEOUT = 20_000L
        private const val INIT_TIMEOUT = 60_000L  // MainApp async init + splash before the wizard
        private const val STEP_TIMEOUT = 15_000L
        private const val IDLE_MS = 300L
        private const val MAX_SCROLLS = 12
        private const val SET_TEXT_ATTEMPTS = 3
        private val EDIT_TEXT: BySelector = By.clazz("android.widget.EditText")
    }
}
