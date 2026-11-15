package app.aaps.pump.omnipod.dash.driver

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodBleManager
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.shared.tests.rx.TestAapsSchedulers
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.subjects.PublishSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The activation talks to the pod outside the command queue. [OmnipodDashManager.isActivationRunning]
 * is what tells the queue to keep away from the BLE link meanwhile (#5219), so it must be set for the
 * whole activation step and cleared on every way out of it.
 */
class OmnipodDashManagerImplActivationTest {

    private val podStateManager: OmnipodDashPodStateManager = mock()
    private val bleManager: OmnipodBleManager = mock()
    private lateinit var sut: OmnipodDashManagerImpl

    @BeforeEach
    fun setUp() {
        sut = OmnipodDashManagerImpl(mock<AAPSLogger>(), podStateManager, bleManager, TestAapsSchedulers())
    }

    @Test
    fun `no activation is running before one is started`() {
        assertThat(sut.isActivationRunning).isFalse()
    }

    /** Pairing is where the queue cut the link: the progress is still NOT_STARTED at that point. */
    @Test
    fun `part 1 counts as running while pairing, and stops when pairing fails`() {
        val pairing = PublishSubject.create<PodEvent>()
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)
        whenever(bleManager.pairNewPod()).thenReturn(pairing)

        val observer = sut.activatePodPart1(null).test()
        assertThat(sut.isActivationRunning).isTrue()

        pairing.onError(IllegalStateException("pairing failed"))
        observer.assertError(IllegalStateException::class.java)
        assertThat(sut.isActivationRunning).isFalse()
    }

    /** Leaving the wizard disposes the activation. The flag must not stay set and block the queue for ever. */
    @Test
    fun `part 1 stops counting as running when it is disposed`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)
        whenever(bleManager.pairNewPod()).thenReturn(PublishSubject.create())

        val observer = sut.activatePodPart1(null).test()
        assertThat(sut.isActivationRunning).isTrue()

        observer.dispose()
        assertThat(sut.isActivationRunning).isFalse()
    }

    @Test
    fun `part 2 counts as running until it completes`() {
        // All part 2 steps are done, so only the connect is left to run.
        val connecting = PublishSubject.create<PodEvent>()
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.CANNULA_INSERTED)
        whenever(bleManager.connect(any<Long>())).thenReturn(connecting)

        val observer = sut.activatePodPart2(mock<BasalProgram>(), null, null).test()
        assertThat(sut.isActivationRunning).isTrue()

        connecting.onComplete()
        observer.assertComplete()
        assertThat(sut.isActivationRunning).isFalse()
    }
}
