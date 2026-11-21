package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Whether an AAPS screen is in front, from the UIKit application state.
 *
 * ## Why "not in the background" rather than "active"
 *
 * Android counts the app as in front while the process lifecycle is at least `STARTED`, which means
 * visible. The iOS state that matches is "anything but `Background`": `Inactive` still means visible
 * - it is what the app is during the app switcher, an incoming call banner or a system alert. Taking
 * `Active` alone would report the app as backgrounded while the user was looking straight at it.
 *
 * ## Why the state is kept in a flow and not read from UIKit on demand
 *
 * [isInForeground] is called by `CarelevoAlarmNotifier` from whatever context an alarm arrives on,
 * and `UIApplication.applicationState` may only be read on the main thread. So the notifications are
 * observed on the main queue and the answer is kept in a [MutableStateFlow], which any thread may
 * read.
 *
 * ## Why it starts as "not in front"
 *
 * The notifications only report changes from the moment they are observed, and this object is built
 * lazily - possibly long after the app became active. The current state is therefore read once on
 * the main queue in [init]. Until that read lands the value is `false`, which is the safe direction:
 * the only thing that depends on it is whether a patch alarm also becomes a phone notification, so a
 * wrong `false` shows a notification the user did not need, while a wrong `true` would hide an
 * occlusion or a patch error. A redundant one is noise; a missing one is a safety problem.
 *
 * The observers are never removed. This is a single instance that lives as long as the process, so
 * there is no point at which removing them would be correct.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosCarelevoAppForeground(
    private val aapsLogger: AAPSLogger
) : CarelevoAppForeground {

    private val state = MutableStateFlow(false)

    init {
        // `willEnterForeground` does not fire for the first foreground of a launch, `didBecomeActive`
        // does, so both are observed. Either one means the app is no longer in the background.
        observe(UIApplicationDidBecomeActiveNotification, inForeground = true)
        observe(UIApplicationWillEnterForegroundNotification, inForeground = true)
        observe(UIApplicationDidEnterBackgroundNotification, inForeground = false)
        // The state as it is right now - see the KDoc on why this cannot simply be the initial value.
        NSOperationQueue.mainQueue.addOperationWithBlock {
            set(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
        }
    }

    override fun isInForeground(): Boolean = state.value

    /** A [MutableStateFlow] already drops a repeated value, so no `distinctUntilChanged` is needed. */
    override fun inForeground(): Flow<Boolean> = state.asStateFlow()

    private fun observe(name: String?, inForeground: Boolean) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = name,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { set(inForeground) }
        )
    }

    private fun set(inForeground: Boolean) {
        if (state.value == inForeground) return
        aapsLogger.debug(LTag.PUMPCOMM, "CareLevo: AAPS in foreground = $inForeground")
        state.value = inForeground
    }
}
