package app.aaps.implementation.scenes

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Scene expiry is **not scheduled on iOS**, and on a client build it never needs to be. This exists
 * so the rest of scenes compiles and the editor works; it is not an implementation of the contract.
 *
 * ## Why nothing calls this
 *
 * The only caller of [SceneExpiryScheduler.schedule] is `SceneExecutor.activate`, which runs on the
 * **master**. `config.AAPSCLIENT` is hardcoded `true` in `IosClientConfig`, and on a client
 * `RoleBranch.prepare`/`commit` send the command to the master rather than calling the local lambda,
 * while `SceneActions.stop` goes through `ClientControlActionDispatcher`. A client runs only
 * `validateActivation`, which is a pure query. So the master activates the scene and schedules its
 * expiry with its own working scheduler, and the client is told the result.
 *
 * It still logs at error rather than doing nothing quietly, because the day iOS ships as something
 * other than a client this becomes reachable, and then it matters - see below.
 *
 * ## What it would cost if iOS were ever a master
 *
 * [SceneExpiryRunner] does more than refresh a screen. At expiry it reverts the two actions whose
 * effect does not end on its own - the SMB toggle, which is a preference with no duration, and the
 * profile switch, whose `EffectiveProfileSwitch` outlives the timed record it came from. Without
 * this callback both would stay applied **indefinitely**, and a chained follow-up scene would never
 * start.
 *
 * Temp target, loop mode and care portal entries are safe either way: those self-expire from their
 * own timestamps.
 *
 * ## Why not a timer
 *
 * iOS has no exact-time background execution. A `UNTimeIntervalNotificationTrigger` fires reliably
 * but only shows a notification; a `BGProcessingTask` runs when iOS decides, which may be hours
 * late or never; an in-process timer works only while the app is alive, which for a follower it
 * usually is not. A real implementation is a combination - timer when alive, notification at the
 * deadline, and an overdue sweep on foreground so the runner executes late rather than never - and
 * that is a product decision about scenes on iOS, not a port. See `_docs/ios_todo.md`.
 *
 * This was twice written up as "a timed scene never ends on iOS". It is not, for the reason above.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class IosSceneExpiryScheduler(
    private val aapsLogger: AAPSLogger
) : SceneExpiryScheduler {

    override fun schedule(sceneName: String, delayMs: Long) {
        aapsLogger.error(
            LTag.UI,
            "Scene expiry cannot be scheduled on iOS: '$sceneName' will NOT end by itself in ${delayMs}ms. " +
                "Its SMB toggle and profile switch would stay applied. Timed scenes must be gated in the UI."
        )
    }

    override fun cancel() {
        // Nothing was ever scheduled, so there is nothing to cancel. Not an error.
    }
}
