package app.aaps.implementation.lifecycle

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ProtectionCheck
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObject

/**
 * Drops a granted PIN or biometric session when the app leaves the screen.
 *
 * The iOS counterpart of `ProcessLifecycleListener`, which does exactly this from Android's
 * `onPause`. Nothing did it here, so an authorization survived backgrounding: picking the phone up
 * again reached settings, the bolus wizard or the whole app - whichever the user had protected -
 * with no prompt.
 *
 * How wide the hole is depends on one setting. `ProtectionTimeout` defaults to a second, so a
 * session expires by itself almost at once and nobody would notice; the user who raised it, up to
 * the maximum of three minutes, is the one who was left unlocked. Narrow, but the wrong direction
 * for a lock, and the narrowness is why it went unseen rather than a reason to leave it.
 *
 * `didEnterBackground` and not `willResignActive`: the second also fires for a notification banner,
 * the control centre or the app switcher being peeked at, none of which is leaving the app.
 * Re-prompting for those would teach the user to enter the PIN without reading why it was asked.
 *
 * Constructed by the shell rather than injected, for the same reason `startBatteryWatch` is: it
 * touches UIKit, which a test binary has no bundle for.
 */
class IosProtectionLifecycle(
    private val aapsLogger: AAPSLogger,
    private val protectionCheck: ProtectionCheck
) {

    private var observer: NSObject? = null

    /** Idempotent, so a shell that is restarted does not end up with two observers. */
    fun start() {
        if (observer != null) return
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = {
                aapsLogger.debug(LTag.CORE, "App went to background, clearing the authorization")
                protectionCheck.resetAuthorization()
            }
        ) as NSObject
    }
}
