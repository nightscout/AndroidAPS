package app.aaps.core.data.model

/**
 * Sealed class representing what happens when a scene ends.
 */
sealed class SceneEndAction {

    /** Show a notification that the scene has ended */
    data object Notification : SceneEndAction()

    /** Suggest activating another scene */
    data class SuggestScene(val sceneId: String) : SceneEndAction()
}
