package app.aaps.core.interfaces.scenes

import app.aaps.core.data.model.Scene
import kotlinx.coroutines.flow.StateFlow

/**
 * Read/write access to the scene catalog (persisted as the `SceneDefinitions` synced blob). Lets `:ui`
 * screens and the expiry worker depend on the catalog through `core:interfaces` rather than the
 * concrete `:implementation` store.
 */
interface SceneStore {

    /** Observable raw scene-definitions blob — emits on add / update / delete (incl. master pushes). */
    val scenesFlow: StateFlow<String>

    /** All scenes (enabled and disabled). */
    fun getScenes(): List<Scene>

    /** Scene by id, or null if not found. */
    fun getScene(id: String): Scene?

    /** Persist the full scene list. */
    fun saveScenes(scenes: List<Scene>)

    /** Add or update a scene (upsert by id). */
    fun saveScene(scene: Scene)

    /** Remove a scene by id. */
    fun deleteScene(id: String)
}
