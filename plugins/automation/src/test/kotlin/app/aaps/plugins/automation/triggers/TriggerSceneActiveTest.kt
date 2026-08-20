package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.asJsonObject
import app.aaps.plugins.automation.elements.ComparatorExists
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.whenever

/** Covers [TriggerSceneActive]: the active×comparator shouldRun matrix, json round-trip, duplicate and labels. */
class TriggerSceneActiveTest : TriggerTestBase() {


    @BeforeEach
    fun prepare() {
        whenever(rh.gs(anyInt())).thenReturn("cmp")
        whenever(rh.gs(anyInt(), anyVararg())).thenReturn("desc")
    }

    private fun trigger(compare: ComparatorExists.Compare) =
        TriggerSceneActive(triggerDeps, sceneApi, compare)

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
        val data = TriggerSceneActive(triggerDeps, sceneApi, ComparatorExists.Compare.NOT_EXISTS).dataJSON().toString()
        val restored = triggerFactory.triggerSceneActive().fromJSON(data) as TriggerSceneActive
        assertThat(restored.comparator.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
        // dataJSON exposes the comparator
        assertThat(data.asJsonObject().lenientString("comparator")).isEqualTo("NOT_EXISTS")
    }

    @Test fun duplicate_copiesComparator() {
        val original = TriggerSceneActive(triggerDeps, sceneApi, ComparatorExists.Compare.EXISTS)
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
