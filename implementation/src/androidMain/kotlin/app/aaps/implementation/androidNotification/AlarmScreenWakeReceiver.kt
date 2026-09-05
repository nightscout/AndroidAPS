package app.aaps.implementation.androidNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * Powers the display on for a background alarm, replacing reliance on `USE_FULL_SCREEN_INTENT`.
 *
 * Fired by an [android.app.AlarmManager.setAlarmClock] alarm scheduled from
 * [AlarmNotificationManager.postFullScreenAlarm]. That alarm-clock context is what lets the
 * deprecated-but-still-functional `ACQUIRE_CAUSES_WAKEUP` wake lock actually turn the screen on from
 * the background — something `Activity.setTurnScreenOn()` cannot do for a background-launched
 * activity (verified on Pixel / Android 15+: the activity is held non-visible until the screen is
 * already on, so it can't self-wake). A sibling `setAlarmClock` → `getActivity` alarm launches
 * [app.aaps.core.interfaces.ui.UiInteraction.errorHelperActivity] once this wake has lit the screen.
 *
 * No app-op or special permission required — only the normal `WAKE_LOCK` permission.
 */
class AlarmScreenWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "aaps:alarm-screen-wake"
        )
        // Short hold — just long enough to power the display and let the alarm activity take over.
        wl.acquire(WAKE_HOLD_MS)
        Log.i(TAG, "Alarm screen wake lock acquired (interactive=${pm.isInteractive})")
    }

    private companion object {

        const val TAG = "AAPSAlarmWake"
        const val WAKE_HOLD_MS = 10_000L
    }
}
