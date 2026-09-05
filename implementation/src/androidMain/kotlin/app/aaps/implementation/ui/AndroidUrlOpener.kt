package app.aaps.implementation.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.UrlOpener
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidUrlOpener @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) : UrlOpener {

    override fun open(url: String) {
        // NEW_TASK because this runs with the application context, not an Activity one: without it
        // Android refuses to start the browser. The call sites are screens that may be hosted
        // anywhere, so they cannot be relied on to supply an Activity.
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // A device with no browser at all. Nothing useful to show the user, so just record it.
            aapsLogger.debug(LTag.UI, "No app can open $url")
        }
    }
}
