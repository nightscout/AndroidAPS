package app.aaps.implementation.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.ActiveSceneState.ScopedRecords
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneLifecycle
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.scenes.ActiveSceneSnapshot
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Covers [ActiveSceneManager] state machine: setActive/markExpired/clearActive/isActive/isExpired,
 * updateScopedRecords, activeSceneSnapshot, and applyActiveScene (null / unknown / known scene).
 */
class ActiveSceneManagerTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var sceneRepository: SceneRepository
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private fun manager(): ActiveSceneManager {
        whenever(preferences.get(StringNonKey.ActiveScene)).thenReturn("")
        whenever(persistenceLayer.observeChanges(TT::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(PS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(RM::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class.java)).thenReturn(emptyFlow())
        return ActiveSceneManager(preferences, sceneRepository, persistenceLayer, aapsLogger)
    }

    private fun state(lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE) = ActiveSceneState(
        scene = Scene(id = "s1", name = "Exercise"),
        activatedAt = 1000L,
        durationMs = 3_600_000L,
        lifecycle = lifecycle
    )

    @Test
    fun setActive_makesSceneActive() {
        val m = manager()
        assertThat(m.isActive()).isFalse()
        val s = state()
        m.setActive(s)
        assertThat(m.isActive()).isTrue()
        assertThat(m.getActiveState()).isEqualTo(s)
        assertThat(m.activeSceneState.value).isEqualTo(s)
    }

    @Test
    fun markExpired_setsExpiredLifecycle() {
        val m = manager()
        m.setActive(state())
        assertThat(m.isExpired()).isFalse()
        m.markExpired()
        assertThat(m.isExpired()).isTrue()
        assertThat(m.getActiveState()!!.lifecycle).isEqualTo(SceneLifecycle.EXPIRED)
    }

    @Test
    fun markExpired_noOpWhenNoActiveScene() {
        val m = manager()
        m.markExpired()
        assertThat(m.getActiveState()).isNull()
    }

    @Test
    fun clearActive_resetsState() {
        val m = manager()
        m.setActive(state())
        m.clearActive()
        assertThat(m.isActive()).isFalse()
        assertThat(m.getActiveState()).isNull()
    }

    @Test
    fun updateScopedRecords_updatesState() {
        val m = manager()
        m.setActive(state())
        m.updateScopedRecords(ScopedRecords(ttNsId = "tt1"))
        assertThat(m.getActiveState()!!.scopedRecords.ttNsId).isEqualTo("tt1")
    }

    @Test
    fun activeSceneSnapshot_reflectsStateOrNull() {
        val m = manager()
        assertThat(m.activeSceneSnapshot()).isNull()
        m.setActive(state())
        val snap = m.activeSceneSnapshot()!!
        assertThat(snap.sceneId).isEqualTo("s1")
        assertThat(snap.durationMs).isEqualTo(3_600_000L)
    }

    @Test
    fun applyActiveScene_nullClears() {
        val m = manager()
        m.setActive(state())
        m.applyActiveScene(null)
        assertThat(m.isActive()).isFalse()
    }

    @Test
    fun applyActiveScene_unknownSceneClears() {
        val m = manager()
        m.setActive(state())
        m.applyActiveScene(ActiveSceneSnapshot(sceneId = "unknown", activatedAt = 0L, durationMs = 0L))
        assertThat(m.isActive()).isFalse()
    }

    @Test
    fun applyActiveScene_knownSceneSetsState() {
        whenever(sceneRepository.getScene("s2")).thenReturn(Scene(id = "s2", name = "Sleep"))
        val m = manager()
        m.applyActiveScene(ActiveSceneSnapshot(sceneId = "s2", activatedAt = 500L, durationMs = 1000L))
        assertThat(m.isActive()).isTrue()
        assertThat(m.getActiveState()!!.scene.id).isEqualTo("s2")
    }
}
