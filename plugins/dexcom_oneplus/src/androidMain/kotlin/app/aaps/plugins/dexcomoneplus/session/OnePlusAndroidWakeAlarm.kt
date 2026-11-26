package app.aaps.plugins.dexcomoneplus.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers

/**
 * Android [OnePlusWakeAlarm]: an exact alarm that is allowed while the device idles, and a receiver
 * that keeps the processor awake for a few seconds when it fires. That is long enough for the
 * session thread to see the time and take its own lock.
 *
 * The receiver is registered in code, not in a manifest: the alarm only matters while the driver's
 * process lives, and a host app that never uses it then needs nothing.
 *
 * @param slot which sensor slot asked for it. Each slot has its own alarm.
 */
class OnePlusAndroidWakeAlarm(context: Context, private val slot: String) : OnePlusWakeAlarm {

    private val appContext = context.applicationContext
    private val action = "${appContext.packageName}.DEXCOM_ONEPLUS_WAKE.$slot"

    @Volatile private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS::DexcomOnePlusWake")
                lock.setReferenceCounted(false)
                lock.acquire(AWAKE_AFTER_ALARM_MS)
                OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] wake alarm fired")
            } catch (t: Throwable) {
                OnePlusLog.w("${OnePlusLogMarkers.SESSION}: [$slot] wake alarm lock ${t.message}")
            }
        }
    }

    override fun wakeAt(epochMs: Long): Boolean =
        try {
            val alarms = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                false
            } else {
                registerReceiver()
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pendingIntent())
                true
            }
        } catch (t: Throwable) {
            // SecurityException without the permission for exact alarms.
            OnePlusLog.w("${OnePlusLogMarkers.SESSION}: [$slot] wake alarm ${t.message}")
            false
        }

    override fun cancel() {
        try {
            (appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent())
        } catch (_: Throwable) {
        }
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(action).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun registerReceiver() {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, IntentFilter(action))
            }
            receiverRegistered = true
        }
    }

    private companion object {

        /** The session thread notices the time within 200 ms and takes its own lock for the connect. */
        const val AWAKE_AFTER_ALARM_MS = 5_000L
    }
}
