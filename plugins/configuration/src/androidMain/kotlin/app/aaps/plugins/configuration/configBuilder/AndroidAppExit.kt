package app.aaps.plugins.configuration.configBuilder

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.configuration.AppExit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.system.exitProcess

/**
 * Ends the app on Android, and starts it again when asked.
 *
 * Starting again works while the app is on the screen. A new task with the launch activity is started
 * before the process ends, and Android starts a fresh process for it. This used to hand the launch intent to
 * `AlarmManager` to fire after the process was gone, and Android blocks that: an app that no longer runs may
 * not open a screen ("Background activity launch blocked", seen on Android 15 on 2026-10-26).
 *
 * From the background - an SMS command, a pump driver - Android does not let an app open a screen at all, so
 * then the process only ends. A broadcast or a job starts it again for the background work.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AndroidAppExit(
    private val context: Context
) : AppExit {

    override fun exit(launchAgain: Boolean) {
        if (launchAgain) startAgain()
        System.runFinalization()
        exitProcess(0)
    }

    private fun startAgain() {
        val launchActivity = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        // A new task, started now, while this process may still open a screen. It also clears the old one.
        context.startActivity(Intent.makeRestartActivityTask(launchActivity))
    }
}
