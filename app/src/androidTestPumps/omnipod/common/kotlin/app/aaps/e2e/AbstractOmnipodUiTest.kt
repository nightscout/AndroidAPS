package app.aaps.e2e

import androidx.test.core.app.ActivityScenario
import app.aaps.ComposeMainActivity
import app.aaps.e2e.ScreenDriver.Companion.COMMAND_TIMEOUT
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The screen test of the Omnipod Dash and the Omnipod 5: the shared test of [AbstractPumpUiTest] with [OmnipodScreens],
 * and the one way out that only a pod has on its screens, "Discard Pod".
 */
abstract class AbstractOmnipodUiTest : AbstractPumpUiTest() {

    override fun screens(): PumpScreens = OmnipodScreens

    /**
     * A pod that does not answer any more, as one that is lost or broken. The deactivation wizard cannot reach it, the
     * command queue gives up on the link, and the wizard then offers "Discard Pod", with a confirm. After it AAPS has
     * no pod and counts nothing more from the old one.
     *
     * The pod is activated without the screens here: the shared test already goes through the activation wizard.
     */
    @Test
    fun aPodThatDoesNotAnswerIsDiscardedFromTheScreens() {
        scenario.pair()
        scenario.checkLinkClosed()
        assertWithMessage("fault").that(scenario.control.setOutOfRange(true)).isTrue()
        scenario.shortenQueueConnectionLimit()

        val activity = ActivityScenario.launch(ComposeMainActivity::class.java)
        try {
            screen.waitForOverview()
            screen.openVia("Manage", expect = "Pump")
            screen.openVia("Pump", expect = OmnipodScreens.pairedMark, exact = true)

            OmnipodScreens.discard(screen)
            screen.waitForExactOrThrow(OmnipodScreens.unpairedMark, COMMAND_TIMEOUT)
            scenario.checkNothingCountedFromRemovedPump()
        } catch (t: Throwable) {
            t.addSuppressed(IllegalStateException("On the screen: " + screen.logScreen("E2E_OmnipodDiscard")))
            throw t
        } finally {
            scenario.control.setOutOfRange(false)
            activity.close()
        }
    }
}
