package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"sceneId":"scene-1"},"type":"ActionRunScene"}"""

class ActionRunSceneTest : ActionsTestBase() {

    private lateinit var sut: ActionRunScene

    private val enabledScene = Scene(id = "scene-1", name = "Sport", isEnabled = true, defaultDurationMinutes = 60)
    private val disabledScene = Scene(id = "scene-2", name = "Sleep", isEnabled = false)

    @BeforeEach fun setUp() {
        whenever(rh.gs(R.string.action_run_scene)).thenReturn("Run scene")
        whenever(rh.gs(R.string.action_run_scene_short)).thenReturn("Run scene: %1\$s")
        whenever(rh.gs(R.string.action_scene_not_found)).thenReturn("Scene not found")
        whenever(rh.gs(R.string.action_scene_disabled)).thenReturn("Scene is disabled")
        sut = ActionRunScene(injector)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.action_run_scene)
    }

    @Test fun shortDescription() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(enabledScene)
        assertThat(sut.shortDescription()).isEqualTo("Run scene: Sport")
    }

    @Test fun doActionSuccess() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.runScene(eq("scene-1"), anyOrNull())).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).runScene(eq("scene-1"), anyOrNull())
    }

    @Test fun doActionFailsWhenSceneNotFound() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.runScene(eq("missing"), anyOrNull())).thenReturn(SceneAutomationResult.SceneNotFound)
        val result = sut.doAction()
        assertThat(result.success).isFalse()
    }

    @Test fun doActionFailsWhenSceneDisabled() = runTest {
        sut.scene.value = "scene-2"
        whenever(sceneApi.runScene(eq("scene-2"), anyOrNull())).thenReturn(SceneAutomationResult.SceneDisabled)
        val result = sut.doAction()
        assertThat(result.success).isFalse()
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

    @Test fun fromJSONIgnoresLegacyDurationField() = runTest {
        // Older saved automations may have stored "durationInMinutes" — must not crash.
        sut.fromJSON("""{"sceneId":"scene-1","durationInMinutes":45}""")
        assertThat(sut.scene.value).isEqualTo("scene-1")
    }

    @Test fun isValidWhenSceneExistsAndEnabled() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(enabledScene)
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun isValidFalseWhenSceneDisabled() = runTest {
        sut.scene.value = "scene-2"
        whenever(sceneApi.getScene("scene-2")).thenReturn(disabledScene)
        assertThat(sut.isValid()).isFalse()
    }

    @Test fun isValidFalseWhenSceneMissing() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.getScene("missing")).thenReturn(null)
        assertThat(sut.isValid()).isFalse()
    }
}
