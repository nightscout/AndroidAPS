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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
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
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(PS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(RM::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        return ActiveSceneManager(preferences, sceneRepository, persistenceLayer, aapsLogger)
    }

    private fun state(lifecycle: SceneLifecycle = SceneLifecycle.ACTIVE) = ActiveSceneState(
        scene = Scene(id = "s1", name = "Exercise"),
        activatedAt = 1000L,
        durationMs = 3_600_000L,
        lifecycle = lifecycle
    )

    /**
     * Pins the persisted shape of the active scene.
     *
     * This preference is what survives a restart: without it a scene that is running when the app dies
     * is never reverted, so its temp target and profile switch stay applied past their end. The format
     * had no test, so a change to how it is written or read would only show up as a scene that quietly
     * never ends.
     *
     * Written against the `org.json` implementation so it keeps holding when that is replaced - a
     * reader that cannot load what the previous version wrote is a lost scene on upgrade.
     */
    @Test
    fun persistThenLoad_restoresEveryFieldIncludingScopedRecords() {
        val captured = argumentCaptor<String>()
        whenever(sceneRepository.getScene("s1")).thenReturn(Scene(id = "s1", name = "Exercise"))
        val m = manager()

        m.setActive(
            state().copy(
                priorSmb = true,
                scopedRecords = ScopedRecords(ttId = 11L, ttNsId = "tt-ns", psId = 22L, teNsId = "te-ns")
            )
        )
        verify(preferences, atLeastOnce()).put(eq(StringNonKey.ActiveScene), captured.capture())
        val persisted = captured.lastValue

        // Reload a fresh manager from exactly those bytes.
        whenever(preferences.get(StringNonKey.ActiveScene)).thenReturn(persisted)
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(PS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(RM::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        val reloaded = ActiveSceneManager(preferences, sceneRepository, persistenceLayer, aapsLogger).getActiveState()

        assertThat(reloaded).isNotNull()
        assertThat(reloaded!!.scene.id).isEqualTo("s1")
        assertThat(reloaded.activatedAt).isEqualTo(1000L)
        assertThat(reloaded.durationMs).isEqualTo(3_600_000L)
        assertThat(reloaded.lifecycle).isEqualTo(SceneLifecycle.ACTIVE)
        assertThat(reloaded.priorSmb).isTrue()
        assertThat(reloaded.scopedRecords.ttId).isEqualTo(11L)
        assertThat(reloaded.scopedRecords.ttNsId).isEqualTo("tt-ns")
        assertThat(reloaded.scopedRecords.psId).isEqualTo(22L)
        assertThat(reloaded.scopedRecords.teNsId).isEqualTo("te-ns")
        // Absent optionals must come back absent, not zero.
        assertThat(reloaded.scopedRecords.rmId).isNull()
        assertThat(reloaded.scopedRecords.psNsId).isNull()
    }

    /**
     * The actual upgrade proof, and the round-trip test above is NOT it: that one writes and reads with
     * the same code, so it would keep passing even if the format changed completely. This feeds a
     * literal string in the shape the previous `org.json` implementation produced.
     *
     * If this breaks, a user upgrading while a scene is running loses it - the scene never expires and
     * its temp target and profile switch stay applied.
     */
    @Test
    fun loadActiveState_readsAPreferenceWrittenByTheOrgJsonVersion() {
        val legacy = """{"sceneId":"s1","activatedAt":1000,"durationMs":3600000,"lifecycle":"ACTIVE",""" +
            """"priorSmb":true,"scopedRecords":{"ttId":11,"ttNsId":"tt-ns","psId":22,"teNsId":"te-ns"}}"""
        whenever(sceneRepository.getScene("s1")).thenReturn(Scene(id = "s1", name = "Exercise"))
        whenever(preferences.get(StringNonKey.ActiveScene)).thenReturn(legacy)
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(PS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(RM::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())

        val loaded = ActiveSceneManager(preferences, sceneRepository, persistenceLayer, aapsLogger).getActiveState()

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.activatedAt).isEqualTo(1000L)
        assertThat(loaded.durationMs).isEqualTo(3_600_000L)
        assertThat(loaded.lifecycle).isEqualTo(SceneLifecycle.ACTIVE)
        assertThat(loaded.priorSmb).isTrue()
        assertThat(loaded.scopedRecords.ttId).isEqualTo(11L)
        assertThat(loaded.scopedRecords.ttNsId).isEqualTo("tt-ns")
        assertThat(loaded.scopedRecords.psId).isEqualTo(22L)
        assertThat(loaded.scopedRecords.teNsId).isEqualTo("te-ns")
        assertThat(loaded.scopedRecords.rmId).isNull()
    }

    @Test
    fun loadActiveState_unreadableJsonYieldsNoActiveScene() {
        whenever(preferences.get(StringNonKey.ActiveScene)).thenReturn("{ not json at all")
        whenever(persistenceLayer.observeChanges(TT::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(PS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(RM::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())

        val m = ActiveSceneManager(preferences, sceneRepository, persistenceLayer, aapsLogger)

        assertThat(m.getActiveState()).isNull()
    }

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
