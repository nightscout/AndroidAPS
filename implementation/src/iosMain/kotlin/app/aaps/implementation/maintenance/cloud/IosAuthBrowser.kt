package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * The Google sign in page, shown over AAPS rather than in Safari.
 *
 * `SFSafariViewController` is presented by this app, so AAPS stays the foreground app while the user
 * signs in - which is what keeps the loopback listener alive to catch the redirect. Handing the URL
 * to `UIApplication.openURL` instead, the way [app.aaps.core.interfaces.ui.UrlOpener] does for
 * ordinary links, switches to Safari and lets iOS suspend AAPS; the redirect then arrives at a port
 * nothing is listening on and the sign in never finishes.
 *
 * It also gets the user's existing Google session from Safari, so most people are not asked to type
 * a password at all - which an embedded web view would not do.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAuthBrowser @Inject constructor(private val aapsLogger: AAPSLogger) : AuthBrowser {

    private var presented: SFSafariViewController? = null

    override fun show(url: String): Boolean {
        val target = NSURL.URLWithString(url)
        if (target == null) {
            aapsLogger.error(LTag.CORE, "$TAG the sign in address could not be read")
            return false
        }
        val host = topViewController()
        if (host == null) {
            aapsLogger.error(LTag.CORE, "$TAG there is no screen to show the sign in over")
            return false
        }

        val safari = SFSafariViewController(uRL = target)
        presented = safari
        host.presentViewController(safari, animated = true, completion = null)
        return true
    }

    override fun dismiss() {
        presented?.dismissViewControllerAnimated(true, completion = null)
        presented = null
    }

    /**
     * The screen currently in front.
     *
     * Walking down from the root rather than presenting on the root itself: the maintenance screen
     * may already have a sheet open, and presenting on a controller that is itself covered does
     * nothing at all - silently, which is the worst way for this to fail.
     */
    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) {
            controller = controller.presentedViewController ?: return controller
        }
    }

    private companion object {

        private const val TAG = "IosAuthBrowser:"
    }
}
