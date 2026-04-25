package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"sceneId":"scene-1"},"type":"ActionDisableScene"}"""

class ActionDisableSceneTest : ActionsTestBase() {

    private lateinit var sut: ActionDisableScene

    private val scene = Scene(id = "scene-1", name = "Sport")

    @BeforeEach fun setUp() {
        whenever(rh.gs(R.string.action_disable_scene)).thenReturn("Disable scene")
        whenever(rh.gs(R.string.action_disable_scene_short)).thenReturn("Disable scene: %1\$s")
        whenever(rh.gs(R.string.action_scene_not_found)).thenReturn("Scene not found")
        sut = ActionDisableScene(injector)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.action_disable_scene)
    }

    @Test fun shortDescription() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        assertThat(sut.shortDescription()).isEqualTo("Disable scene: Sport")
    }

    @Test fun doActionSuccess() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.setEnabled(eq("scene-1"), eq(false))).thenReturn(SceneAutomationResult.Success)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(sceneApi, times(1)).setEnabled(eq("scene-1"), eq(false))
    }

    @Test fun doActionFailsWhenSceneNotFound() = runTest {
        sut.scene.value = "missing"
        whenever(sceneApi.setEnabled(eq("missing"), eq(false))).thenReturn(SceneAutomationResult.SceneNotFound)
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

    @Test fun isValidWhenSceneExists() = runTest {
        sut.scene.value = "scene-1"
        whenever(sceneApi.getScene("scene-1")).thenReturn(scene)
        assertThat(sut.isValid()).isTrue()
    }

    @Test fun isValidTrueEvenWhenSceneAlreadyDisabled() = runTest {
        // Disabling an already-disabled scene is a benign no-op — must be valid.
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
