package app.aaps.wear.comm

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.WearApp

/**
 * Starts a foreground service in a way that survives a refusal.
 *
 * An app that targets Android 12 or newer may not start a foreground service while it is in the
 * background. The watch app is often started in the background: by a complication, a tile or the
 * watch face. The refusal is an exception, and from `WearApp.onCreate` it ended the app at every
 * start: 28 times in three minutes on a Galaxy Watch 5, until the phone's link started the app in
 * a way that is allowed.
 *
 * So the refusal is caught, and the start is tried once more from an exact alarm a few seconds
 * later. The receiver of an exact alarm is one of the cases where Android allows the start.
 */
object ForegroundStart {

    /** The watch's alarm service takes no alarm that is nearer than 5 s. */
    private const val RETRY_AFTER_MS = 5_000L

    /** @return false when Android refused the start. */
    fun startService(context: Context, intent: Intent, aapsLogger: AAPSLogger, retryWhenRefused: Boolean = true): Boolean =
        attempt(context, aapsLogger, retryWhenRefused) { context.startForegroundService(intent) }

    /**
     * Runs [action], which starts a foreground service or makes a running service a foreground one.
     *
     * @param retryWhenRefused false for the try that comes from the alarm itself, so a watch that
     *   refuses that one too is not asked again and again.
     * @return false when Android refused it.
     */
    fun attempt(context: Context, aapsLogger: AAPSLogger, retryWhenRefused: Boolean = true, action: () -> Unit): Boolean =
        try {
            action()
            true
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || e !is ForegroundServiceStartNotAllowedException) throw e
            if (retryWhenRefused && retryFromAlarm(context)) {
                aapsLogger.info(LTag.WEAR, "Foreground service refused in the background, trying again from an alarm")
            } else {
                aapsLogger.warn(LTag.WEAR, "Foreground service refused in the background, not tried again: ${e.message}")
            }
            false
        }

    private fun retryFromAlarm(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) return false
        val retry = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ForegroundStartReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + RETRY_AFTER_MS, retry)
        return true
    }
}

/** The alarm of [ForegroundStart]: starts the watch app's foreground services again. */
class ForegroundStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? WearApp)?.startForegroundServices()
    }
}
