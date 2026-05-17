package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for scene definitions. Reads/writes scene list from SharedPreferences
 * via StringNonKey.SceneDefinitions.
 */
@Singleton
class SceneRepository @Inject constructor(
    private val preferences: Preferences
) {

    /** Observable flow of all scene definitions */
    val scenesFlow: StateFlow<String> = preferences.observe(StringNonKey.SceneDefinitions)

    /** Get current list of scenes */
    fun getScenes(): List<Scene> = preferences.get(StringNonKey.SceneDefinitions).toScenes()

    /** Get a scene by ID */
    fun getScene(id: String): Scene? = getScenes().find { it.id == id }

    /** Save the full list of scenes */
    fun saveScenes(scenes: List<Scene>) {
        preferences.put(StringNonKey.SceneDefinitions, scenes.toJson())
    }

    /** Add or update a scene */
    fun saveScene(scene: Scene) {
        val scenes = getScenes().toMutableList()
        val index = scenes.indexOfFirst { it.id == scene.id }
        if (index >= 0) {
            scenes[index] = scene
        } else {
            scenes.add(scene)
        }
        saveScenes(scenes)
    }

    /** Delete a scene by ID */
    fun deleteScene(id: String) {
        saveScenes(getScenes().filter { it.id != id })
    }
}
