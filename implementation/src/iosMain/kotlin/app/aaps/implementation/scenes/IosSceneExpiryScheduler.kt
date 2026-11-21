package app.aaps.implementation.scenes

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.delayUntil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Ends a timed scene on iOS: on time while the app is awake, late but reliably when it is not.
 *
 * ## Why a timer is allowed here, when [SceneExpiryScheduler] says it is not
 *
 * That warning was written when scene expiry had no backstop, and it no longer has none.
 * `PostCalculationRunner.endOverdueScene` calls [app.aaps.core.interfaces.scenes.SceneExpiryCheck.endIfOverdue]
 * before every loop run, so a scene whose scheduled end never fired is ended by the next glucose
 * calculation instead. That gate is `config.APS`, which is exactly the build this class matters on -
 * a follower receives a scene's end from its master and schedules nothing.
 *
 * So the promise is kept by three things together, which is the arrangement
 * [app.aaps.implementation.profile.ProfileSwitchScheduler] already relies on:
 *
 * 1. this timer, while the app is alive - the scene ends at its time on the wall clock ([delayUntil]),
 *    not after an amount of awake time, which a locked phone stretches by however long it slept;
 * 2. the foreground sweep below, so opening AAPS ends an overdue scene at once rather than at the
 *    next reading;
 * 3. `PostCalculationRunner`, every loop run, which is the durable one: it does not care whether
 *    this process is the one that scheduled anything.
 *
 * What that costs is lateness, bounded by how often glucose arrives - about five minutes with a CGM
 * running. It is not "silently never", which is what the previous placeholder here would have given
 * an iOS master, and what makes an exercise profile outlive the exercise.
 *
 * ## What is deliberately not here
 *
 * A local notification posted at the deadline, so that a phone whose app was killed *and* whose CGM
 * has stopped still hears about the scene. It is the right refinement, and the write-up in
 * `_docs/ios_blockers.md` names it. It is left out on purpose: posting it from here would be another
 * bespoke notification path, and pump and alarm notifications are being unified into core rather
 * than written once per feature. It belongs to that work, not to this class.
 *
 * ## Why the runner arrives as a provider
 *
 * `SceneExecutor.activate` calls [schedule], and [SceneExpiryRunner] depends on `SceneExecutor`, so
 * taking the runner directly would be a dependency cycle. Asking for it only when the deadline
 * arrives breaks it, the same way `IosRunningModeExpiryTimer` does.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosSceneExpiryScheduler(
    private val aapsLogger: AAPSLogger,
    private val runner: () -> SceneExpiryRunner,
    private val scope: CoroutineScope,
    private val clock: Clock
) : SceneExpiryScheduler {

    private var pending: Job? = null

    init {
        // Opening the app is the one moment a user expects to see the truth, and it costs nothing:
        // `endIfOverdue` returns immediately unless a scene is genuinely past its end.
        observeForeground(UIApplicationWillEnterForegroundNotification)
        observeForeground(UIApplicationDidBecomeActiveNotification)
    }

    override fun schedule(sceneName: String, delayMs: Long) {
        cancel()
        aapsLogger.debug(LTag.UI, "Scene '$sceneName' ends in ${delayMs}ms")
        val endMs = clock.now().toEpochMilliseconds() + delayMs
        pending = scope.launch {
            clock.delayUntil(endMs)
            // Not guarded by a time check: the wait already waited it out, and the runner takes the
            // lock and re-reads the active scene, so a scene that ended another way is a no-op there.
            aapsLogger.debug(LTag.UI, "Scene '$sceneName' reached its end, ending it")
            runner().run(sceneName)
        }
    }

    override fun cancel() {
        pending?.cancel()
        pending = null
    }

    private fun observeForeground(name: String?) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { sweep() }
        )
    }

    /**
     * Ends the active scene if it is already past its end.
     *
     * Failures are logged and swallowed. This runs on the way into the app, and a scene that cannot
     * be ended must not stop the app opening - the next loop run tries again anyway.
     */
    private fun sweep() {
        scope.launch {
            runCatching { runner().endIfOverdue() }
                .onFailure { aapsLogger.error(LTag.UI, "Ending an overdue scene on foreground failed: $it") }
        }
    }
}
