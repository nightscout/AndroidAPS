package app.aaps.implementation.scenes

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.aps.Loop
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SceneExpiryWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var activeSceneManager: ActiveSceneManager
    @Mock lateinit var sceneExecutor: SceneExecutor
    @Mock lateinit var sceneRepository: SceneRepository
    @Mock lateinit var loop: Loop
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker() =
        SceneExpiryWorker(
            context, workerParameters, aapsLogger, fabricPrivacy, activeSceneManager, sceneExecutor, sceneRepository,
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
        whenever(rh.gs(any<Int>())).thenReturn("msg")
        whenever(rh.gs(any<Int>(), anyOrNull())).thenReturn("msg")
        whenever(rh.gs(any<Int>(), anyOrNull(), anyOrNull())).thenReturn("msg")
        whenever(workerParameters.inputData).thenReturn(workDataOf(SceneExpiryWorker.KEY_SCENE_NAME to "MyScene"))
    }

    @Test
    fun `no active state returns success without expiry`() = runTest {
        whenever(activeSceneManager.getActiveState()).thenReturn(null)

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(sceneExecutor, never()).onExpiry()
    }

    @Test
    fun `already expired returns success without re-running onExpiry`() = runTest {
        whenever(activeSceneManager.getActiveState()).thenReturn(mock<ActiveSceneState>())
        whenever(activeSceneManager.isExpired()).thenReturn(true)

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(sceneExecutor, never()).onExpiry()
    }

    @Test
    fun `non-chain end action runs onExpiry without chaining`() = runTest {
        val state = stateWithEndAction(SceneEndAction.Notification)
        whenever(activeSceneManager.getActiveState()).thenReturn(state)
        whenever(activeSceneManager.isExpired()).thenReturn(false)

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(sceneExecutor).onExpiry()
        verify(sceneRepository, never()).getScene(any())
    }

    @Test
    fun `chain to deleted target runs onExpiry and looks up target`() = runTest {
        val state = stateWithEndAction(SceneEndAction.ChainScene("target"))
        whenever(activeSceneManager.getActiveState()).thenReturn(state)
        whenever(activeSceneManager.isExpired()).thenReturn(false)
        whenever(sceneRepository.getScene("target")).thenReturn(null)

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(sceneExecutor).onExpiry()
        verify(sceneRepository).getScene("target")
    }
}
