package app.aaps.implementation.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.UrlOpener
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSURL

/**
 * Opens a link with `UIApplication`, the iOS counterpart of the Android `ACTION_VIEW` intent.
 *
 * Two checks happen before the address reaches the platform, because iOS drops a bad one silently
 * and the user would just see nothing happen:
 *
 * - `NSURL` returns null for text that is not an address at all, such as one with a space in it.
 * - An address with no scheme, like `www.example.com`, parses fine but `openURL` refuses it.
 *
 * Both are logged rather than shown. The interface asks for fire and forget, and a link that cannot
 * be opened is not worth interrupting the user for.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosUrlOpener @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val launcher: UrlLauncher = SystemUrlLauncher()
) : UrlOpener {

    override fun open(url: String) {
        val parsed = NSURL.URLWithString(url)
        if (parsed == null) {
            aapsLogger.debug(LTag.UI, "Not an address, nothing to open: $url")
            return
        }
        if (parsed.scheme.isNullOrEmpty()) {
            aapsLogger.debug(LTag.UI, "Address has no scheme, iOS would refuse it: $url")
            return
        }
        launcher.launch(parsed)
    }
}
