package app.aaps.core.interfaces.scenes

/**
 * Ends the active scene when its time is over but its scheduled end has not run yet.
 *
 * The scheduled end is a WorkManager job on Android, and Android can keep it waiting when the app has
 * used up its job time quota - since Android 16 also with a foreground service running. The loop
 * does not wait for that quota any more, so without this check it would go on dosing with the scene's
 * SMB setting after the scene should have ended. The loop step calls it before each BG-triggered run.
 */
interface SceneExpiryCheck {

    /** Does nothing when no scene is active, the scene has no end time, or it has ended already. */
    suspend fun endIfOverdue()
}
