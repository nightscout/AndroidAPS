package app.aaps.core.data.model

/**
 * Sealed class representing what happens when a scene ends.
 */
sealed class SceneEndAction {

    /** Show a notification that the scene has ended */
    data object Notification : SceneEndAction()

    /** Auto-activate another scene when this one expires (ignored on manual end) */
    data class ChainScene(val sceneId: String) : SceneEndAction()
}
