package app.aaps.pump.omnipod.omnipod5

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.O5BleManager
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.O5PodStateManager
import app.aaps.pump.omnipod.omnipod5.history.O5History
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * The two parts of the pod activation, as the driver runs them for the queue.
 *
 * [O5BleManager.isActivationRunning] keeps the link open while a part runs (Dash #5219). It must be set before the
 * first command and cleared on every way out. A part that fails answers with a result and does not throw: a throw
 * would reach the queue as a plain "error" without the reason.
 */
internal class O5PodActivationTest {

    private val bleManager: O5BleManager = mock()
    private val podStateManager: O5PodStateManager = mock()
    private val history: O5History = mock()
    private val pumpSync: PumpSync = mock()
    private val rh: ResourceHelper = mock()
    private val enactResult: PumpEnactResult = mock(defaultAnswer = Answers.RETURNS_SELF)

    private val sut = O5PodActivation(
        bleManager, podStateManager, mock<Preferences>(), rh, history, pumpSync, mock<NotificationManager>(),
        { enactResult }, mock<AAPSLogger>()
    )

    @Test
    fun `pairing marks the activation as running and clears it when the pairing fails`() {
        whenever(podStateManager.ltk).thenReturn(null)
        whenever(bleManager.pairNewPod()).thenReturn(Observable.error(IllegalStateException("pairing failed")))

        runBlocking { sut.initializePod() }

        inOrder(bleManager) {
            verify(bleManager).isActivationRunning = true
            verify(bleManager).pairNewPod()
            verify(bleManager).isActivationRunning = false
        }
        verify(enactResult).success(false)
        verifyNoInteractions(history)
    }

    @Test
    fun `pairing clears the running mark when it throws`() {
        whenever(podStateManager.ltk).thenReturn(null)
        whenever(bleManager.pairNewPod()).doAnswer { throw IllegalStateException("no adapter") }

        runBlocking { sut.initializePod() }

        inOrder(bleManager) {
            verify(bleManager).isActivationRunning = true
            verify(bleManager).isActivationRunning = false
        }
        verify(enactResult).success(false)
    }

    @Test
    fun `the cannula part marks the activation as running and clears it when it fails`() {
        // No profile, so part 2 fails at its first line.
        runBlocking { whenever(pumpSync.expectedPumpState()).doAnswer { throw IllegalStateException("No profile set") } }

        runBlocking { sut.insertCannula() }

        inOrder(bleManager) {
            verify(bleManager).isActivationRunning = true
            verify(bleManager).isActivationRunning = false
        }
        verify(enactResult).success(false)
        verify(bleManager, never()).sendCommand(any(), any())
    }
}
