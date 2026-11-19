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
 * between [schedule] and the deadline, and a plain in-process timer on its own does not.
 *
 * **There is a backstop now, and this paragraph used to say there was not.**
 * `PostCalculationRunner.endOverdueScene` calls [app.aaps.core.interfaces.scenes.SceneExpiryCheck.endIfOverdue]
 * before every loop run, so a scheduled end that never fired is caught by the next glucose
 * calculation. That makes scene expiry the same shape as
 * [app.aaps.implementation.profile.ProfileSwitchScheduler], which is lightweight because
 * KeepAliveWorker catches what it misses. An implementation may therefore be a timer *plus* that
 * sweep; what it may not be is a timer alone, trusting a process that iOS is free to kill.
 *
 * Android satisfies it with WorkManager, which fires on time whether or not the app is alive. iOS
 * has no equivalent, so `IosSceneExpiryScheduler` is the combination above: exact while awake, and
 * late rather than never otherwise.
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
