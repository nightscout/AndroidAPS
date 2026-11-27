package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The two "is there a scene" questions and how they differ once a scene expires but its banner is
 * still up — the state that used to block every automation gated on "no scene running" (#5059).
 */
class SceneAutomationApiImplTest : TestBase() {

    @Mock lateinit var sceneRepository: SceneRepository
    @Mock lateinit var sceneExecutor: SceneExecutor
    @Mock lateinit var activeSceneManager: ActiveSceneManager
    @Mock lateinit var sceneChainTargetResolver: SceneChainTargetResolver
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var rh: ResourceHelper

    private fun createSut() = SceneAutomationApiImpl(
        sceneRepository, sceneExecutor, activeSceneManager, sceneChainTargetResolver, notificationManager, rh
    )

    private fun givenScene(active: Boolean, expired: Boolean) {
        whenever(activeSceneManager.isActive()).thenReturn(active)
        whenever(activeSceneManager.isExpired()).thenReturn(expired)
    }

    @Test
    fun `a running scene is active and stoppable`() {
        givenScene(active = true, expired = false)

        assertThat(createSut().isAnySceneActive()).isTrue()
        assertThat(createSut().hasSceneToStop()).isTrue()
    }

    @Test
    fun `an expired scene is no longer active but is still stoppable`() {
        // Its effects were reverted by onExpiry; only the "ended" banner is left. Automation gated on
        // "no scene running" must be free to run, while the user (or the watch) can still dismiss it.
        givenScene(active = true, expired = true)

        assertThat(createSut().isAnySceneActive()).isFalse()
        assertThat(createSut().hasSceneToStop()).isTrue()
    }

    @Test
    fun `with no scene at all both answers are no`() {
        givenScene(active = false, expired = false)

        assertThat(createSut().isAnySceneActive()).isFalse()
        assertThat(createSut().hasSceneToStop()).isFalse()
    }

    // --- stopScene(id): the stop for automation, which may end only the scene it names ---

    private fun givenRunning(sceneId: String, expired: Boolean = false) {
        givenScene(active = true, expired = expired)
        whenever(activeSceneManager.getActiveState())
            .thenReturn(ActiveSceneState(scene = Scene(id = sceneId, name = "Driving"), activatedAt = 0L, durationMs = 0L))
    }

    @Test
    fun `only the running scene counts as active by id`() {
        givenRunning("driving")

        assertThat(createSut().isSceneActive("driving")).isTrue()
        assertThat(createSut().isSceneActive("sport")).isFalse()
    }

    @Test
    fun `an expired scene is not active by id`() {
        givenRunning("driving", expired = true)

        assertThat(createSut().isSceneActive("driving")).isFalse()
    }

    @Test
    fun `stopScene ends the named scene when it runs`() = runTest {
        givenRunning("driving")
        whenever(sceneExecutor.deactivate()).thenReturn(SceneExecutionResult(success = true))

        assertThat(createSut().stopScene("driving")).isEqualTo(SceneAutomationResult.Success)
        verify(sceneExecutor).deactivate()
    }

    @Test
    fun `stopScene leaves another running scene alone`() = runTest {
        // The rule names "driving", but the user runs "sport". Ending it would undo a scene the
        // user started for another reason.
        givenRunning("sport")

        assertThat(createSut().stopScene("driving")).isEqualTo(SceneAutomationResult.Success)
        verify(sceneExecutor, never()).deactivate()
        verify(sceneExecutor, never()).dismiss()
    }

    @Test
    fun `stopScene does not dismiss the banner of an expired scene`() = runTest {
        // Unlike stopActiveScene, which serves the user's own "Dismiss". A rule must not clear a
        // banner the user has not seen yet.
        givenRunning("driving", expired = true)

        assertThat(createSut().stopScene("driving")).isEqualTo(SceneAutomationResult.Success)
        verify(sceneExecutor, never()).deactivate()
        verify(sceneExecutor, never()).dismiss()
    }

    @Test
    fun `stopScene does nothing when no scene runs`() = runTest {
        givenScene(active = false, expired = false)

        assertThat(createSut().stopScene("driving")).isEqualTo(SceneAutomationResult.Success)
        verify(sceneExecutor, never()).deactivate()
    }

    @Test
    fun `stopScene reports a failed revert`() = runTest {
        givenRunning("driving")
        whenever(sceneExecutor.deactivate()).thenReturn(SceneExecutionResult(success = false, errorMessage = "Some reverts failed"))

        assertThat(createSut().stopScene("driving")).isEqualTo(SceneAutomationResult.Failed("Some reverts failed"))
    }
}
