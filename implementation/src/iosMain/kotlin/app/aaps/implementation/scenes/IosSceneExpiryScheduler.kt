package app.aaps.implementation.scenes

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Scene expiry is **not scheduled on iOS**. This exists so the rest of scenes compiles and the
 * editor works; it is not an implementation of the contract.
 *
 * ## What would happen if a timed scene were activated
 *
 * [SceneExpiryRunner] does more than refresh a screen. At expiry it reverts the two actions whose
 * effect does not end on its own - the SMB toggle, which is a preference with no duration, and the
 * profile switch, whose `EffectiveProfileSwitch` outlives the timed record it came from. Without
 * this callback both stay applied **indefinitely**, and a chained follow-up scene never starts.
 *
 * Temp target, loop mode and care portal entries are safe either way: those self-expire from their
 * own timestamps.
 *
 * So this logs at error rather than debug. A silent no-op here would be the exact failure the
 * migration rules warn about, on therapy settings.
 *
 * ## Why not a timer
 *
 * iOS has no exact-time background execution. A `UNTimeIntervalNotificationTrigger` fires reliably
 * but only shows a notification; a `BGProcessingTask` runs when iOS decides, which may be hours
 * late or never; an in-process timer works only while the app is alive, which for a follower it
 * usually is not. A real implementation is a combination - timer when alive, notification at the
 * deadline, and an overdue sweep on foreground so the runner executes late rather than never - and
 * that is a product decision about scenes on iOS, not a port. See `_docs/ios_blockers.md`.
 *
 * **Before scenes ship on iOS, activation of a *timed* scene has to be gated in the UI.**
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSceneExpiryScheduler @Inject constructor(
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
