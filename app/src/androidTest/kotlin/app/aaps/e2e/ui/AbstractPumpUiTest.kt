package app.aaps.e2e.ui

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.aaps.ComposeMainActivity
import app.aaps.core.keys.BooleanKey
import app.aaps.di.ResetGraphRule
import app.aaps.di.testGraphs
import app.aaps.e2e.RetryRule
import app.aaps.e2e.ScreenDriver
import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The screens of a pump driver, driven through the real Compose UI against its emulator: Manage → Pump → pair through
 * the wizard → history → suspend and resume → the driver's other flows → unpair. Each step is checked on the emulated
 * pump and in AAPS, the same for every pump. A pump's class only gives its [PumpUnderTest] and its [PumpScreens].
 *
 * The scenario tests (`AbstractPumpScenarioTest`) drive the same drivers headless, through the wizard ViewModels; this
 * one adds the screens and their button wiring. Set up and torn down by [PumpScenario], so it starts unpaired and leaves
 * nothing behind, and the emulator runs at the fast speed unless `-e emulatorSpeed real`.
 *
 * Site rotation is off, so no wizard has a SITE_LOCATION step; [PumpScenario.bringUp] activates a profile and leaves a
 * single insulin, so no wizard has a profile or insulin step either. English only.
 */
abstract class AbstractPumpUiTest {

    // RetryRule outermost: a UI timeout under CI load gets one more try; see [RetryRule].
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(ResetGraphRule())

    abstract fun pumpUnderTest(): PumpUnderTest
    abstract fun screens(): PumpScreens

    protected lateinit var scenario: PumpScenario

    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    protected val screen get() = ScreenDriver(device)
    private val pump get() = testGraphs.activePlugin.activePumpInternal

    @Before
    fun setUp() {
        scenario = PumpScenario(pumpUnderTest())
        scenario.bringUp()
        testGraphs.preferences.put(BooleanKey.SiteRotationManagePump, false)
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
    }

    @After
    fun tearDown() {
        scenario.tearDown()
    }

    @Test
    fun pumpScreens_pairUseAndUnpair() {
        val screens = screens()
        assertWithMessage("paired before the test").that(pump.isInitialized()).isFalse()

        val activity = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            screen.waitForOverview()
            screen.openVia("Manage", expect = PUMP_MANAGEMENT)
            screen.openVia(PUMP_MANAGEMENT, expect = screens.unpairedMark, exact = true)

            screens.pair(screen, scenario)
            screen.waitForExactOrThrow(screens.pairedMark, COMMAND_TIMEOUT)
            scenario.awaitTrue("AAPS has the pump", COMMAND_TIMEOUT) { pump.isInitialized() }

            if (screens.visitHistory(screen)) {
                // The system Back, as a gesture: it must close only the sub-screen. Without a back handler in the
                // driver the app pops the whole pump page (found in Equil; Omnipod had the same fix).
                screen.pressBack()
                if (!screen.waitForExact(screens.pairedMark))
                    throw AssertionError("System Back on the history screen did not lead to the pump overview")
            } else Log.i(TAG, "${pump.javaClass.simpleName}: no history screen")
            screen.waitForExactOrThrow(screens.pairedMark)

            if (screens.suspend(screen)) {
                scenario.awaitTrue("the pump is suspended", COMMAND_TIMEOUT) { scenario.control.state().suspended }
                screens.resume(screen)
                scenario.awaitTrue("the pump runs again", COMMAND_TIMEOUT) { !scenario.control.state().suspended }
            } else Log.i(TAG, "${pump.javaClass.simpleName}: no suspend on its screens")
            screen.waitForExactOrThrow(screens.pairedMark)

            screens.otherFlows(screen, scenario)
            screen.waitForExactOrThrow(screens.pairedMark, COMMAND_TIMEOUT)
            assertWithMessage("AAPS has the pump after its other flows").that(pump.isInitialized()).isTrue()

            screens.unpair(screen, scenario)
            screen.waitForExactOrThrow(screens.unpairedMark, COMMAND_TIMEOUT)
            scenario.awaitTrue("AAPS has no pump any more", COMMAND_TIMEOUT) { !pump.isInitialized() }
        } catch (t: Throwable) {
            // Also in the error, which the test output keeps: on CI the logcat can have rotated away by the time it is read.
            t.addSuppressed(IllegalStateException("On the screen: " + screen.logScreen("E2E_${pump.javaClass.simpleName}")))
            throw t
        } finally {
            activity.close()
        }
    }

    private companion object {

        const val TAG = "E2E_PumpUi"
        const val PUMP_MANAGEMENT = "Pump"
    }
}
