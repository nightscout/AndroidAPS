package app.aaps.core.data.model

/**
 * Scene (Situation Preset) definition.
 * Bundles multiple actions (TT, profile, SMB, loop mode, CarePortal) into a single activation.
 * Scenes are always temporary — they have a duration or are manually ended.
 */
data class Scene(
    /** Unique identifier */
    val id: String,
    /** User-defined display name */
    val name: String,
    /** Icon resource name (material icon name for future use) */
    val icon: String = "star",
    /** Default duration in minutes (user can override at activation) */
    val defaultDurationMinutes: Int = 60,
    /** Actions to execute when scene is activated */
    val actions: List<SceneAction> = emptyList(),
    /** What happens when the scene ends */
    val endAction: SceneEndAction = SceneEndAction.Notification,
    /** Whether this scene can be deleted (false for templates until customized) */
    val isDeletable: Boolean = true,
    /** Display order (lower = higher priority) */
    val sortOrder: Int = 0
)
