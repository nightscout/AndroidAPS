package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.plugins.automation.AutomationStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"sceneId":"scene-1"},"type":"ActionStopScene"}"""

class ActionStopSceneTest : ActionsTestBase() {

    private lateinit var sut: ActionStopScene

    private val scene = Scene(id = "scene-1", name = "Driving")

    @BeforeEach fun setUp() {
        sut = ActionStopScene(aapsLogger, text, { pumpEnactResultProvider() }, sceneApi, sceneIconResolver, triggerDeps)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.action_stop_scene)
    }

    @Test fun shortDescription() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        assertThat(sut.shortDescription()).isEqualTo("Stop scene: Driving")
    }

    @Test fun doActionStopsTheRunningScene() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.isSceneActive("scene-1")).thenReturn(true)
        whenever(sceneApi.stopScene(eq("scene-1"))).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).stopScene(eq("scene-1"))
    }

    @Test fun doActionLeavesAnotherSceneAlone() = runTest {
        // Another scene runs, or none: the selected scene is not active, so nothing may be stopped.
        sut.scene.value = "scene-1"
        whenever(sceneApi.isSceneActive("scene-1")).thenReturn(false)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("Scene is not running")
        verify(sceneApi, never()).stopScene(any())
        verify(sceneApi, never()).stopActiveScene()
    }

    @Test fun doActionFailsWhenTheStopFails() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.isSceneActive("scene-1")).thenReturn(true)
        whenever(sceneApi.stopScene(eq("scene-1"))).thenReturn(SceneAutomationResult.Failed("Some reverts failed"))
        val result = sut.doAction()
        assertThat(result.success).isFalse()
        assertThat(result.comment).isEqualTo("Some reverts failed")
    }

    @Test fun preconditionTest() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        // Selected scene runs -> automation may run
        whenever(sceneApi.isSceneActive("scene-1")).thenReturn(true)
        assertThat(sut.precondition!!.shouldRun()).isTrue()
        // Selected scene does not run -> whole automation is skipped
        whenever(sceneApi.isSceneActive("scene-1")).thenReturn(false)
        assertThat(sut.precondition!!.shouldRun()).isFalse()
    }

    @Test fun preconditionFollowsTheSelectedScene() = runTest {
        // The precondition is built once, before a scene is picked. It must ask about the scene
        // picked later, not about the empty value from construction time.
        whenever(sceneApi.isSceneActive("scene-2")).thenReturn(true)
        sut.scene.value = "scene-2"
        assertThat(sut.precondition!!.shouldRun()).isTrue()
    }

    @Test fun preconditionDescription() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        assertThat(sut.precondition!!.friendlyDescription()).isEqualTo("Scene Driving is running")
    }

    @Test fun hasDialog() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.scene.value = "scene-1"
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"sceneId":"scene-1"}""")
        assertThat(sut.scene.value).isEqualTo("scene-1")
    }

    @Test fun factoryBuildsItFromStoredJson() = runTest {
        val restored = actionFactory.instantiate(Json.parseToJsonElement(STRING_JSON).jsonObject)
        assertThat(restored).isInstanceOf(ActionStopScene::class.java)
        assertThat((restored as ActionStopScene).scene.value).isEqualTo("scene-1")
    }

    @Test fun isValidWhenSceneExists() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun isValidTrueEvenWhenSceneIsDisabled() = runTest {
        // A scene can be disabled while it runs — it must still be possible to stop it.
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene.copy(isEnabled = false))
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun isValidFalseWhenSceneMissing() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.getScene("missing")).thenReturn(null)
        assertThat(sut.isValid()).isFalse()
    }
}
