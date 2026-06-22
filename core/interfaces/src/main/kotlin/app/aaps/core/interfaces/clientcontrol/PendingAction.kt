package app.aaps.core.interfaces.clientcontrol

/**
 * What the single app-level client-control modal is currently showing: the action's [progress] plus a
 * short, already-localized [label] describing what was attempted ("set temp target", "activate scene
 * Sleep") so a failure reads "Couldn't {label}: {reason}". One at a time (round-trips are
 * single-in-flight). The label is built on the initiating device, so it's in that device's locale.
 */
data class PendingAction(
    val progress: ActionProgress,
    val label: String
)
