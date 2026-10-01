package app.aaps.implementation.scenes

/**
 * Fires the end of a timed scene at `activatedAt + durationMs`, even if the app is not running then.
 *
 * This is the one platform call that kept [SceneExecutor] on Android. The rule around it - when a
 * scene ends, what is reverted, what is chained - is the same everywhere and lives in shared code;
 * only "wake me up at time T" differs per platform.
 *
 * ## The promise, and why it has to be a durable one
 *
 * A scene applies a temp target, a profile switch and an SMB setting, and the only thing that takes
 * them off again is this callback. If it does not fire, those stay applied indefinitely: the user
 * gets an exercise profile that never ends. So an implementation must survive the app being killed
 * between [schedule] and the deadline. A plain in-process timer does NOT satisfy this contract and
 * must not be used to implement it - unlike
 * [app.aaps.implementation.profile.ProfileSwitchExpiryScheduler], which can be lightweight because
 * KeepAliveWorker is its backstop. Scene expiry has no backstop.
 *
 * Android satisfies it with WorkManager. There is deliberately no iOS implementation yet: iOS has no
 * equivalent that fires at an exact time in the background, so scenes are visibly absent there
 * rather than present with an expiry that silently never runs.
 */
interface SceneExpiryScheduler {

    /**
     * Wake up in [delayMs] and end the scene named [sceneName].
     *
     * Replaces any previously scheduled expiry - there is at most one active scene, so there is at
     * most one pending expiry. Must not throw; a scheduler that cannot schedule logs and returns.
     */
    fun schedule(sceneName: String, delayMs: Long)

    /** Drops a pending expiry, for a scene that ended early. A no-op when nothing is pending. */
    fun cancel()
}
