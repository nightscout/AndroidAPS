package app.aaps.e2e

import app.aaps.e2e.scenario.PumpScenario
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import app.aaps.pump.omnipod.emulator.OmnipodEmulatorTransport
import com.google.common.truth.Truth.assertWithMessage

/**
 * The screens the Omnipod Dash and the Omnipod 5 share, see [AbstractPumpUiTest]: the pod overview, the activation
 * wizard (Fill Pod → Initialize Pod → Attach Pod → Insert Cannula → Pod Activated), the pod history and the
 * deactivation wizard (Deactivate Pod → Deactivating Pod → Pod Deactivated).
 *
 * The two action steps of the activation and the one of the deactivation start their command by themselves when the
 * step opens, and show "Next" only when the command is done. So the tap that opens such a step is done once.
 *
 * Neither driver has a stop of the insulin on its screens, so there is no suspend here. A pod that is stopped shows
 * "Resume Delivery" on its overview: [resumeDelivery], which `AbstractOmnipodUiTest` tests on its own.
 */
object OmnipodScreens : PumpScreens {

    override val unpairedMark = "Activate Pod"
    override val pairedMark = "Play Test Beep"

    override fun pair(screen: ScreenDriver, scenario: PumpScenario) {
        screen.openVia(unpairedMark, expect = "Fill Pod")          // overview → the first step of the wizard

        // "Next" opens Initialize Pod, which pairs the pod and primes it. The step's title shows at once, so a tap
        // that was taken is not done again; the first tap after the wizard opens can come too early and is.
        screen.openVia("Next", expect = "Initialize Pod", exact = true)
        screen.waitForVisibleOrThrow("Next", POD_COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "Attach Pod")

        // "Next" asks first, and "OK" opens Insert Cannula, which sets the basal and inserts the cannula: once.
        screen.openVia("Next", expect = "the cannula will be inserted")
        screen.click("OK")
        screen.waitForExactOrThrow("Insert Cannula")
        screen.waitForVisibleOrThrow("Next", POD_COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "Pod Activated")

        screen.openVia("Finish", expect = pairedMark, exact = true)   // back to the overview, with a pod
    }

    /** The history holds the two activation commands the pod took. Its title is the text of the button that opens it. */
    override fun visitHistory(screen: ScreenDriver): Boolean {
        screen.openVia("Pod History", expect = "Insert Cannula")
        screen.assertVisibleContains("Initialize Pod")
        return true
    }

    /**
     * The test beep from its overview button, and one pump setting saved to the pod: the units of the low reservoir
     * alert are raised by one on the pump settings screen, and the driver programs the pod's alerts again. The system
     * Back on the settings screen must lead to the pod overview.
     */
    override fun otherFlows(screen: ScreenDriver, scenario: PumpScenario) {
        val pod = (scenario.control as OmnipodEmulatorTransport).emulator

        val beepsBefore = pod.state.beepCommands
        screen.click(pairedMark)
        scenario.awaitTrue("the pod took the test beep", ScreenDriver.COMMAND_TIMEOUT) { pod.state.beepCommands > beepsBefore }
        scenario.awaitQueueIdle()

        assertWithMessage("the activation programmed the low reservoir alert").that(pod.state.alertEnabled[LOW_RESERVOIR_SLOT]).isTrue()
        val tenthsBefore = pod.state.alertTrigger[LOW_RESERVOIR_SLOT]
        screen.openVia("Settings", expect = LOW_RESERVOIR_UNITS)
        // The settings open with their groups closed: a tap on the group's name opens it. Once, a second closes it.
        screen.click("Alerts")
        screen.click("increment $LOW_RESERVOIR_UNITS by 1")
        scenario.awaitTrue("the pod has the low reservoir alert one unit higher", ScreenDriver.COMMAND_TIMEOUT) {
            pod.state.alertTrigger[LOW_RESERVOIR_SLOT] == tenthsBefore + 10
        }
        scenario.awaitQueueIdle()
        screen.pressBack()
        if (!screen.waitForExact(pairedMark)) throw AssertionError("System Back on the pump settings did not lead to the pod overview")
    }

    override fun unpair(screen: ScreenDriver, scenario: PumpScenario) {
        // The wizard's first step has the same title as the button, so its "Cancel" is the sign that it opened.
        screen.openVia("Deactivate Pod", expect = "Cancel", exact = true)

        // "Next" asks first, and "OK" opens Deactivating Pod, which sends the command: once.
        screen.openVia("Next", expect = "can not be used again")
        screen.click("OK")
        screen.waitForExactOrThrow("Deactivating Pod")
        screen.waitForVisibleOrThrow("Next", POD_COMMAND_TIMEOUT)
        screen.openVia("Next", expect = "Pod Deactivated")

        screen.openVia("Finish", expect = unpairedMark, exact = true)   // back to the overview, with no pod
    }

    /**
     * From the overview of a pod that does not answer to the overview with no pod. The deactivation fails once the
     * command queue has given up on the link, and only then does the wizard show "Discard Pod". Its confirm dialog has
     * the same title, so the tap is done once and the dialog is known by its text.
     */
    fun discard(screen: ScreenDriver) {
        screen.openVia("Deactivate Pod", expect = "Cancel", exact = true)
        screen.openVia("Next", expect = "can not be used again")
        screen.click("OK")
        screen.waitForExactOrThrow("Deactivating Pod")

        screen.waitForExactOrThrow("Discard Pod", DISCARD_OFFER_TIMEOUT)
        screen.click("Discard Pod")
        screen.assertVisibleContains("you will not be able to communicate with it")
        screen.click("OK")
        screen.waitForExactOrThrow("Pod Discarded")

        screen.openVia("Finish", expect = unpairedMark, exact = true)
    }

    /** On the overview of a stopped pod: one tap on "Resume Delivery". It starts a pod command, so it is done once. */
    fun resumeDelivery(screen: ScreenDriver) {
        screen.click(RESUME_DELIVERY)
    }

    /** The button goes away once the pod gives basal again. It must be on the screen before, or this proves nothing. */
    fun awaitNoResumeDelivery(screen: ScreenDriver) {
        screen.waitForGoneOrThrow(RESUME_DELIVERY, ScreenDriver.COMMAND_TIMEOUT)
    }

    private const val RESUME_DELIVERY = "Resume Delivery"

    /** The command queue first gives up on the link, then the deactivation fails and the wizard offers the discard. */
    private const val DISCARD_OFFER_TIMEOUT = 240_000L

    /** The first activation step scans, pairs and primes; at the real speed of the emulator that is over a minute. */
    private const val POD_COMMAND_TIMEOUT = 150_000L

    /** The units of the low reservoir alert, on the pump settings screen. The pod takes them in tenths of a unit. */
    private const val LOW_RESERVOIR_UNITS = "Number of units"

    /** The alert slot of the low reservoir alert, the index of `AlertType.LOW_RESERVOIR` in the driver. */
    private const val LOW_RESERVOIR_SLOT = 4
}
