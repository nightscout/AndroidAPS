package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.InitProgress
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Was `SceneExpiryWorkerTest`. The body it covers is now [SceneExpiryRunner] in commonMain, so the test
 * no longer has to build a `WorkerParameters` or compare `ListenableWorker.Result` values - it names the
 * outcome the work reports instead.
 */
class SceneExpiryRunnerTest : TestBaseWithProfile() {

    @Mock lateinit var activeSceneManager: ActiveSceneManager
    @Mock lateinit var sceneExecutor: SceneExecutor
    @Mock lateinit var sceneRepository: SceneRepository
    @Mock lateinit var loop: Loop

    private fun runner() =
        SceneExpiryRunner(
            aapsLogger, activeSceneManager, sceneExecutor, sceneRepository,
            loop, activePlugin, profileFunction, rh, notificationManager, config
        )

    private fun stateWithEndAction(action: SceneEndAction): ActiveSceneState {
        val scene: Scene = mock {
            on { name } doReturn "MyScene"
            on { id } doReturn "s1"
            on { endAction } doReturn action
        }
        return mock { on { this.scene } doReturn scene }
    }

    @BeforeEach
    fun setup() {
        whenever(config.appInitialized).thenReturn(true)
    }

    @Test
    fun `no active state skips without expiry`() = runTest {
        whenever(activeSceneManager.getActiveState()).thenReturn(null)

        val outcome = runner().run("MyScene")

        assertThat(outcome).isInstanceOf(WorkOutcome.Skipped::class.java)
        verify(sceneExecutor, never()).onExpiry()
    }

    @Test
    fun `already expired skips without re-running onExpiry`() = runTest {
        whenever(activeSceneManager.getActiveState()).thenReturn(mock<ActiveSceneState>())
        whenever(activeSceneManager.isExpired()).thenReturn(true)

        val outcome = runner().run("MyScene")

        assertThat(outcome).isInstanceOf(WorkOutcome.Skipped::class.java)
        verify(sceneExecutor, never()).onExpiry()
    }

    /**
     * The gate that used to be `Result.retry()`. It has to stay a retry and not become a skip or a
     * failure: this run is the only thing that ends the scene, so dropping it leaves the scene's temp
     * target and profile switch applied with nothing left to remove them.
     */
    @Test
    fun `an app that has not finished starting asks to be run again`() = runTest {
        whenever(config.appInitialized).thenReturn(false)
        // awaitInitialized() waits on this flow when init has not finished. One that never reports done
        // is an app that never comes up, so the wait runs out - on runTest's virtual clock, at once.
        whenever(config.initProgressFlow).thenReturn(MutableStateFlow(InitProgress(done = false)))

        val outcome = runner().run("MyScene")

        assertThat(outcome).isInstanceOf(WorkOutcome.Retry::class.java)
        verify(sceneExecutor, never()).onExpiry()
    }

    @Test
    fun `non-chain end action runs onExpiry without chaining`() = runTest {
        val state = stateWithEndAction(SceneEndAction.Notification)
        whenever(activeSceneManager.getActiveState()).thenReturn(state)
        whenever(activeSceneManager.isExpired()).thenReturn(false)

        val outcome = runner().run("MyScene")

        assertThat(outcome).isEqualTo(WorkOutcome.Success)
        verify(sceneExecutor).onExpiry()
        verify(sceneRepository, never()).getScene(any())
    }

    @Test
    fun `chain to deleted target runs onExpiry and looks up target`() = runTest {
        val state = stateWithEndAction(SceneEndAction.ChainScene("target"))
        whenever(activeSceneManager.getActiveState()).thenReturn(state)
        whenever(activeSceneManager.isExpired()).thenReturn(false)
        whenever(sceneRepository.getScene("target")).thenReturn(null)

        val outcome = runner().run("MyScene")

        assertThat(outcome).isEqualTo(WorkOutcome.Success)
        verify(sceneExecutor).onExpiry()
        verify(sceneRepository).getScene("target")
    }
}
