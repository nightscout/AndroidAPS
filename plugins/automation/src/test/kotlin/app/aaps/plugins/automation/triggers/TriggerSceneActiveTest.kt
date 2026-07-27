package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorExists
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers [TriggerSceneActive]: the active×comparator shouldRun matrix, json round-trip, duplicate and labels. */
class TriggerSceneActiveTest : TriggerTestBase() {

    private val sceneApi: SceneAutomationApi = mock()

    @BeforeEach
    fun prepare() {
        whenever(rh.gs(anyInt())).thenReturn("cmp")
        whenever(rh.gs(anyInt(), anyVararg())).thenReturn("desc")
    }

    private fun trigger(compare: ComparatorExists.Compare) =
        TriggerSceneActive(injector, compare).also { it.sceneApi = sceneApi }

    @Test fun shouldRun_whenSceneActive() = runTest {
        whenever(sceneApi.isAnySceneActive()).thenReturn(true)
        assertThat(trigger(ComparatorExists.Compare.EXISTS).shouldRun()).isTrue()
        assertThat(trigger(ComparatorExists.Compare.NOT_EXISTS).shouldRun()).isFalse()
    }

    @Test fun shouldRun_whenNoSceneActive() = runTest {
        whenever(sceneApi.isAnySceneActive()).thenReturn(false)
        assertThat(trigger(ComparatorExists.Compare.EXISTS).shouldRun()).isFalse()
        assertThat(trigger(ComparatorExists.Compare.NOT_EXISTS).shouldRun()).isTrue()
    }

    @Test fun json_dataRoundTrip() {
        val data = TriggerSceneActive(injector, ComparatorExists.Compare.NOT_EXISTS).dataJSON().toString()
        val restored = TriggerSceneActive(injector).fromJSON(data) as TriggerSceneActive
        assertThat(restored.comparator.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
        // dataJSON exposes the comparator
        assertThat(JSONObject(data).getString("comparator")).isEqualTo("NOT_EXISTS")
    }

    @Test fun duplicate_copiesComparator() {
        val original = TriggerSceneActive(injector, ComparatorExists.Compare.EXISTS)
        val copy = original.duplicate() as TriggerSceneActive
        assertThat(copy.comparator.value).isEqualTo(ComparatorExists.Compare.EXISTS)
    }

    @Test fun labelsAndTypes() {
        val t = trigger(ComparatorExists.Compare.EXISTS)
        assertThat(t.friendlyName()).isEqualTo(R.string.trigger_scene_active)
        assertThat(t.friendlyDescription()).isEqualTo("desc")
        assertThat(t.composeIcon()).isNotNull()
        assertThat(t.elementType()).isEqualTo(ElementType.SCENE)
    }
}
