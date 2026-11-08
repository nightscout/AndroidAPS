package app.aaps.e2e

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
import app.aaps.core.keys.BooleanKey
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.pump.equil.di.EquilAccessors
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Drives the **Equil wizards and overview through their real Compose UI**, against the in-tree Equil emulator, each
 * screen's button clicked with uiautomator:
 * - PAIR: Manage → Pump → "Pair" → ASSEMBLE → BLE scan → PASSWORD → FILL → ATTACH → AIR → CONFIRM, the genuine ~13
 *   command activation chain, until the overview repaints as paired;
 * - the History screen with both tabs;
 * - Suspend and Resume on the overview, checked on the emulated pump;
 * - CHANGE_INSULIN with the same infusion set: no AIR step, the branch pairing never takes;
 * - UNPAIR: DETACH → CONFIRM → result, until the overview offers "Pair" again.
 *
 * The scenario tests (`EquilScenarioTest`) drive the same driver headless through the wizard ViewModels; this one adds
 * the screens and their button wiring. Set up and torn down by [PumpScenario], so it starts unpaired and leaves nothing
 * behind, and the emulator runs at the fast speed unless `-e emulatorSpeed real`.
 *
 * ## Reaching exactly these steps
 * `initializeWorkflow` inserts conditional steps: a PROFILE_GATE when no profile is active, a SELECT_INSULIN when more
 * than one insulin is configured, and a SITE_LOCATION when `SiteRotationManagePump` is on. [PumpScenario.bringUp]
 * activates a profile and leaves a single insulin, and [setUp] turns site rotation off, so none of the three appear.
 *
 * ## Fragility (read before editing)
 * Selectors match case-insensitively against text OR content-desc; steps are gated on a **unique** in-content string
 * before the (often ambiguously-labelled, e.g. several "Next") button is clicked; the FILL/AIR "Next" only enables after
 * its async command settles, so [openVia] retries. A click that starts a command chain is clicked once, never retried.
 * It is English-only.
 */
@RunWith(AndroidJUnit4::class)
class EquilActivationWizardUiTest {

    // RetryRule outermost: a flaky UI timeout self-heals on a fresh attempt; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    private lateinit var scenario: PumpScenario

    private val equilManager get() = (testGraphs.rootGraph as EquilAccessors).equilManager

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        scenario = PumpScenario(EquilPumpUnderTest())
        scenario.bringUp()
        // Keep the wizards to their fixed steps: no SITE_LOCATION.
        testGraphs.preferences.put(BooleanKey.SiteRotationManagePump, false)
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
    }

    @After
    fun tearDown() {
        scenario.tearDown()
    }

    @Test
    fun equilWizards_workThroughTheirScreens() {
        assertThat(equilManager.isActivationCompleted()).isFalse()

        val activity = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            waitForOverview()

            // Manage → Pump → the Equil overview; unpaired, so it offers "Pair".
            openVia("Manage", expect = PUMP_MANAGEMENT)
            openVia(PUMP_MANAGEMENT, expect = "Pair")

            pair()
            history()
            suspendAndResume()
            changeReservoir()
            unpair()
        } catch (t: Throwable) {
            logScreen("E2E_EQUIL_WIZARD")
            throw t
        } finally {
            activity.close()
        }
    }

    private fun pair() {
        openVia("Pair", expect = "Pair Equil patch pump")   // overview "Pair" → ASSEMBLE header

        // Every plain step transition retries its click until the next screen's unique text appears (openVia): a
        // single "Next" tap is routinely dropped during Compose recomposition, and those transitions are idempotent.
        openVia("Next", expect = "Select your pump")        // ASSEMBLE → BLE_SCAN

        // BLE_SCAN: the emulator advertises one device row "Equil - A#####".
        openVia("Equil -", expect = "Enter password for")   // tap device row (prefix) → PASSWORD

        // PASSWORD: an empty code is accepted; "Pair" runs getVersion → pair → pumpSettings, a command chain, so one click.
        click("Pair")
        assertVisibleContains("Please prime the reservoir", COMMAND_TIMEOUT)  // FILL

        // FILL: "Prime/Fill" runs the fill loop; the emulator's resistance completes it, then the primary relabels to "Next".
        click("Prime/Fill")
        waitForVisibleOrThrow("Next", COMMAND_TIMEOUT)
        openVia("Next", expect = "attach the pump to the base")   // FILL → ATTACH
        openVia("Next", expect = "purge the air")                 // ATTACH → AIR (equil_hint1 body)

        // AIR: "Purge air" runs the air command; once it settles the "Next" enables and runs the alarm → basal → time →
        // firmware tail into CONFIRM. Each attempt's wait outlasts the tail chain, so it never double-fires.
        click("Purge air")
        openVia("Next", expect = "to your body", attempts = 6)    // AIR → CONFIRM

        // CONFIRM: "Finish" runs insulin → model → final settings → saveActivation, then the wizard closes back to the
        // overview, now paired.
        openVia("Finish", expect = "Unpair device", attempts = 4)
        assertThat(equilManager.isActivationCompleted()).isTrue()
    }

    /** The History screen with both tabs: command history and pump events. */
    private fun history() {
        openVia("History", expect = "History action")
        click("Equil history")
        assertVisibleContains("Equil history")
        device.pressBack()
        waitForVisibleOrThrow("Unpair device")
    }

    /** The overview's Suspend and Resume, each checked on the emulated pump. */
    private fun suspendAndResume() {
        openVia("Suspend", expect = "Resume delivery")
        scenario.awaitTrue("the pump is suspended", COMMAND_TIMEOUT) { scenario.control.state().suspended }
        click("Resume delivery")
        scenario.awaitTrue("the pump runs again", COMMAND_TIMEOUT) { !scenario.control.state().suspended }
        waitForExactOrThrow("Suspend")
    }

    /** A new reservoir in the same infusion set: no AIR step, and no cannula change recorded. */
    private fun changeReservoir() {
        openVia("Change reservoir", expect = "Will you also put on a new infusion set")
        click("No, only reservoir or battery")
        // "Next" runs the change command, which moves the plunger back: once.
        click("Next")
        assertVisibleContains("Please assemble the pump", COMMAND_TIMEOUT)       // ASSEMBLE ("Change cartridge")
        openVia("Next", expect = "Please prime the reservoir")                     // ASSEMBLE → FILL
        click("Prime/Fill")
        waitForVisibleOrThrow("Next", COMMAND_TIMEOUT)
        openVia("Next", expect = "attach the pump to the base")                    // FILL → ATTACH
        openVia("Next", expect = "to your body")                                   // ATTACH → CONFIRM, no AIR
        openVia("Finish", expect = "Unpair device", attempts = 4)                  // back to the paired overview
        assertThat(equilManager.isActivationCompleted()).isTrue()
        assertThat(scenario.control.state().suspended).isFalse()
    }

    private fun unpair() {
        openVia("Unpair device", expect = "plunger to retract")    // UNPAIR_DETACH
        // "Next" runs the detach command, "Finish" the unpair: each once.
        click("Next")
        assertVisibleContains("relinquish control", COMMAND_TIMEOUT)  // UNPAIR_CONFIRM
        click("Finish")
        assertVisibleContains("Device unpaired", COMMAND_TIMEOUT)    // the result
        click("Finish")
        waitForExactOrThrow("Pair", COMMAND_TIMEOUT)                 // the overview offers "Pair" again
        assertThat(equilManager.equilState?.serialNumber ?: "").isEmpty()
    }

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

    /** A text that is exactly [label]: "Pair" must not match "Unpair device", nor "Suspend" match "Suspended". */
    private fun waitForExactOrThrow(label: String, timeout: Long = STEP_TIMEOUT) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (device.findObject(byText(label)) != null) return
            device.waitForIdle(IDLE_MS)
        }
        error("Timed out after ${timeout}ms looking for exactly '$label'")
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
            // Back, not a scrim tap: the tap goes to the scrim node centre, which is ON the sheet once it is
            // tall enough (see AbstractDanaEmulatorUiTest). The find guards it, so Back needs a sheet to be up.
            runCatching { if (device.findObject(byDesc("Close sheet")) != null) device.pressBack() }
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

    companion object {

        private const val PKG = "info.nightscout.androidaps"
        private const val PUMP_MANAGEMENT = "Pump"

        // Generous: the test launches the full ComposeMainActivity, so first paint can lag under CI load.
        private const val INIT_TIMEOUT = 120_000L
        private const val STEP_TIMEOUT = 30_000L
        private const val IDLE_MS = 300L
        private const val STALE_RETRIES = 10
        private const val STALE_SETTLE_MS = 700L

        // Command-driven transitions: each spans several pump commands.
        private const val COMMAND_TIMEOUT = 60_000L
    }
}
