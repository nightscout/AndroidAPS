package app.aaps.implementation.ui

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The one step of opening a link that needs UIKit.
 *
 * Split out so [IosUrlOpener] can be tested. Everything else it does - deciding whether a string is
 * a usable address at all - is ordinary logic, but `UIApplication` cannot be touched outside a
 * running app, so a test would fail on the call rather than on the logic.
 */
fun interface UrlLauncher {

    /** Hands [url] to the platform. Nothing is reported back: see [app.aaps.core.interfaces.ui.UrlOpener.open]. */
    fun launch(url: NSURL)
}

/**
 * The real one: `UIApplication`, on the main queue.
 *
 * The hop to the main queue is not optional. `UIApplication.sharedApplication` may only be touched
 * from the main thread, and callers here are view models that can run anywhere.
 */
class SystemUrlLauncher : UrlLauncher {

    override fun launch(url: NSURL) {
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }
}
