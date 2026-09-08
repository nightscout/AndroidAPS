package app.aaps.plugins.configuration.configBuilder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.configuration.AppExit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Ends the app on Android, restarting it through `AlarmManager` when asked.
 *
 * Lifted out of `ConfigBuilderImpl` unchanged, which is what let the rest of that class move to
 * commonMain. A process cannot restart itself directly, so the trick is to hand the launch intent to
 * `AlarmManager` a moment in the future and then die: the alarm fires after the process is gone and
 * starts it again.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidAppExit @Inject constructor(
    private val context: Context
) : AppExit {

    override fun exit(launchAgain: Boolean) {
        if (launchAgain) scheduleStart()
        System.runFinalization()
        kotlin.system.exitProcess(0)
    }

    private fun scheduleStart() {
        // fetch the packageManager so we can get the default launch activity
        context.packageManager?.let { pm ->
            //create the intent with the default start activity for your application
            pm.getLaunchIntentForPackage(context.packageName)?.let { startActivity ->
                startActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                //create a pending intent so the application is restarted after System.exit(0) was called.
                // We use an AlarmManager to call this intent in 100ms
                val pendingIntentId = 2233445
                val pendingIntent = PendingIntent.getActivity(context, pendingIntentId, startActivity, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)
            }
        }
    }
}
