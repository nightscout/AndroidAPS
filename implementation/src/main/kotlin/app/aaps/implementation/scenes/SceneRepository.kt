package app.aaps.implementation.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.interfaces.scenes.SceneStore
import app.aaps.core.interfaces.scenes.Scenes
import app.aaps.core.keys.StringNonKey
import app.aaps.core.objects.extensions.toJson
import app.aaps.core.objects.extensions.toScenes
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for scene definitions. Reads/writes the scene list from SharedPreferences via
 * StringNonKey.SceneDefinitions, which syncs as a plain blob over the generic key-sync path
 * (Bidirectional). No in-memory cache — [scenesFlow] is a [StateFlow] backed by preferences,
 * so external writes (incl. a master push) propagate automatically.
 */
@Singleton
class SceneRepository @Inject constructor(
    private val preferences: Preferences
) : Scenes, SceneStore {

    /** Observable raw flow of the scene-definitions pref. */
    override val scenesFlow: StateFlow<String> = preferences.observe(StringNonKey.SceneDefinitions)

    /** All scenes. */
    override fun getScenes(): List<Scene> = preferences.get(StringNonKey.SceneDefinitions).toScenes()

    /** Get a scene by ID. */
    override fun getScene(id: String): Scene? = getScenes().find { it.id == id }

    /** Save the full list of scenes. */
    override fun saveScenes(scenes: List<Scene>) {
        preferences.put(StringNonKey.SceneDefinitions, scenes.toJson())
    }

    /** Add or update a scene (upsert by id). */
    override fun saveScene(scene: Scene) {
        val scenes = getScenes().toMutableList()
        val index = scenes.indexOfFirst { it.id == scene.id }
        if (index >= 0) scenes[index] = scene else scenes.add(scene)
        saveScenes(scenes)
    }

    /** Remove a scene by id. */
    override fun deleteScene(id: String) {
        val scenes = getScenes().toMutableList()
        if (scenes.removeAll { it.id == id }) saveScenes(scenes)
    }
}
