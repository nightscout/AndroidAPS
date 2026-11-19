package app.aaps.implementation.lifecycle

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Reports whether an AAPS screen is in front, from the UIKit application state.
 *
 * A function and not a class, so nothing needs it in a constructor: three classes in this module ask the same question
 * for different reasons, and a shared dependency would mean a stand-in for each of them in a test binary, where there
 * is no Mockito. What is shared here is only the UIKit detail, which is the part that is easy to get wrong.
 *
 * ## "Not in the background", not "active"
 *
 * Android counts the app as in front while the process lifecycle is at least `STARTED`, which means visible. The iOS
 * state that matches is anything but `Background`: `Inactive` still means visible - it is what the app is during the
 * app switcher, an incoming call banner or a system alert. `Active` alone would report the app as backgrounded while
 * the user was looking straight at it.
 *
 * ## It starts as "not in front"
 *
 * The notifications only report changes from the moment they are observed, and a caller may be built long after the
 * app became active, so the state as it is now is read once on the main queue. [onChange] is called with `false`
 * first, which is the safe direction for every caller here: a notification that was not needed is noise, while a
 * missing one is a safety problem.
 *
 * `applicationState` may only be read on the main thread, which is the other reason for the read to be an operation on
 * the main queue rather than the initial value. In a test binary the main queue is never drained, so that block simply
 * does not run and the state stays `false` - which is why a test may call the caller's own setter instead.
 *
 * The observers are never removed. Every caller is a single instance that lives as long as the process, so there is no
 * point at which removing them would be correct.
 */
internal fun observeAppForeground(onChange: (Boolean) -> Unit) {
    onChange(false)
    // `willEnterForeground` does not fire for the first foreground of a launch, `didBecomeActive` does, so both are
    // observed. Either one means the app is no longer in the background.
    observe(UIApplicationDidBecomeActiveNotification, true, onChange)
    observe(UIApplicationWillEnterForegroundNotification, true, onChange)
    observe(UIApplicationDidEnterBackgroundNotification, false, onChange)
    NSOperationQueue.mainQueue.addOperationWithBlock {
        onChange(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    }
}

/** The notification names are `String?` in the Kotlin/Native bindings, hence the nullable parameter. */
private fun observe(name: String?, inForeground: Boolean, onChange: (Boolean) -> Unit) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = name,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
        usingBlock = { onChange(inForeground) }
    )
}
