package app.aaps.e2e

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.pump.emulator.EmulatedAlarm
import app.aaps.di.testGraphs
import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The Carelevo screens, see [AbstractPumpUiTest]. */
class CarelevoUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = CarelevoPumpUnderTest()
    override fun screens(): PumpScreens = CarelevoScreens

    /**
     * A patch alarm is a pump alert (`Alerts`): the alarm screen opens with the patch's own step, Silence takes the
     * sound from the card but keeps the card, and the step - here the discard an occlusion asks for - ends the alarm,
     * which closes the screen.
     */
    @Test
    fun aPatchAlarmOpensTheAlarmScreenThatSilencesAndActs() {
        val activity = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            screen.waitForOverview()
            screen.openVia("Manage", expect = PUMP_MANAGEMENT)
            screen.openVia(PUMP_MANAGEMENT, expect = CarelevoScreens.unpairedMark, exact = true)
            CarelevoScreens.pair(screen, scenario)
            screen.waitForExactOrThrow(CarelevoScreens.pairedMark, COMMAND_TIMEOUT)

            assertWithMessage("the patch raises an occlusion").that(scenario.control.raise(EmulatedAlarm.OCCLUSION)).isTrue()
            // AAPS learns of it at the latest at its next connection to the patch.
            scenario.readStatusOnNewLink()

            // Only the alarm screen of a pump alert has this button.
            screen.waitForVisibleOrThrow(SILENCE, COMMAND_TIMEOUT)
            scenario.awaitTrue("the alarm card rings") { alertCards().any { it.sound != null } }

            screen.clickButton(SILENCE)
            scenario.awaitTrue("the card stays, without sound") { alertCards().isNotEmpty() && alertCards().none { it.sound != null } }

            screen.clickButton(DEACTIVATE)
            scenario.awaitTrue("the alarm ended with the patch", COMMAND_TIMEOUT) { alertCards().isEmpty() }
            scenario.awaitTrue("the alarm screen closed") { device.findObject(By.text(SILENCE)) == null }
            // Not the overview: back in front, the main screen may first show its permissions sheet on a test emulator.
            scenario.awaitTrue("AAPS has no patch any more", COMMAND_TIMEOUT) { !testGraphs.activePlugin.activePumpInternal.isInitialized() }
        } catch (t: Throwable) {
            t.addSuppressed(IllegalStateException("On the screen: " + screen.logScreen("E2E_CarelevoAlarm")))
            throw t
        } finally {
            activity.close()
        }
    }

    private fun alertCards() = testGraphs.notificationManager.notifications.value.filter { it.id == NotificationId.ALERT }

    private val device: UiDevice get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {

        const val PUMP_MANAGEMENT = "Pump"
        const val SILENCE = "Silence for 30 min"
        const val DEACTIVATE = "Deactivate Patch"
    }
}

/**
 * The Carelevo patch flow and overview: activation (PATCH_START → SET_AMOUNT → PATCH_CONNECT with its scan and connect
 * dialog → SAFETY_CHECK → PATCH_ATTACH → NEEDLE_INSERTION), Suspend with its pause time dialog, Resume with its confirm
 * dialog, and Discard. The driver has no history screen, and a patch change is a discard and a new activation.
 *
 * Several labels are also a title or part of a sentence ("Safety Check" is the toolbar title, the section title and
 * the button; "Confirm" is in the connect dialog's title), so command buttons are tapped with
 * [ScreenDriver.clickButton].
 */
object CarelevoScreens : PumpScreens {

    override val unpairedMark = "Activate Patch"
    override val pairedMark = "Discard Patch"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(unpairedMark, expect = "Fill Patch with Insulin")    // PATCH_START
        screen.openVia("Next", expect = "Range : 50 ~ 300 U")              // SET_AMOUNT, 300 U by default
        screen.openVia("Next", expect = "Power On and Connect")            // PATCH_CONNECT

        // The scan always runs its full 5 s, then the connect dialog shows the patch it found.
        screen.clickButton("Search Patch")
        screen.assertVisibleContains("If the patch detected below is correct", COMMAND_TIMEOUT)
        screen.clickButton("Confirm")                                        // pairs
        screen.assertVisibleContains("The patch has been connected successfully", COMMAND_TIMEOUT)

        screen.clickButton("Safety Check")
        screen.assertVisibleContains("Safety Check Completed", COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "Disinfect Patch Site")             // PATCH_ATTACH
        screen.openVia("Next", expect = "Remove Safety Cap")                // NEEDLE_INSERTION

        screen.clickButton("Confirm Insertion")
        screen.assertVisibleContains("Needle insertion complete", COMMAND_TIMEOUT)
        // Sends the basal only 10 s after the needle was found, then the flow ends on the overview.
        screen.clickButton("Applicator Removed")
    }

    override fun suspend(screen: ScreenDriver): Boolean {
        screen.clickButton("Suspend Infusion")
        screen.assertVisibleContains("Please set the infusion pause time")   // 30 minutes is chosen
        screen.clickButton("Confirm")
        screen.waitForExactOrThrow("Resume Infusion", COMMAND_TIMEOUT)
        return true
    }

    override fun resume(screen: ScreenDriver) {
        screen.clickButton("Resume Infusion")
        screen.assertVisibleContains("Would you like to resume infusion")
        screen.clickButton("Confirm")
        screen.waitForExactOrThrow("Suspend Infusion", COMMAND_TIMEOUT)
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.clickButton(pairedMark)
        screen.assertVisibleContains("Deactivate patch and start new patch")
        screen.clickButton("Confirm")
    }
}
