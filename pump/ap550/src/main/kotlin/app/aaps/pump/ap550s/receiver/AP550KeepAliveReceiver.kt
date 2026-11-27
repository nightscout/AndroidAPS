package app.aaps.pump.ap550s.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import app.aaps.pump.ap550s.events.EventAP550CheckState
import dev.zacsweers.metro.Inject

/**
 * Wakes the AP550 driver every 15 seconds.
 *
 * WorkManager cannot go below a 15 minute interval and a plain timer does not survive Doze, so the
 * driver keeps its own exact alarm chain instead: each alarm posts [EventAP550CheckState] for
 * [AP550ExecutionService] and then schedules the next one.
 *
 * The chain belongs to the driver and is only running while the driver is: [AP550PumpPlugin.onStart]
 * starts it and `AP550PumpPlugin.onStop` cancels it, so a user on another pump gets no wakeups from
 * this module.
 */
class AP550KeepAliveReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        aapsLogger.debug(LTag.PUMP, "AP550 keep alive")
        rxBus.send(EventAP550CheckState())
        startAliveTask(context)
    }

    companion object {

        private const val ACTION = "app.aaps.pump.ap550s.ACTION_KEEP_ALIVE"
        private const val REQUEST_CODE = 0
        private val INTERVAL_MS = T.secs(15).msecs()

        /** Schedules the next wakeup. Call from the driver's `onStart`. */
        fun startAliveTask(context: Context) {
            schedule(context, System.currentTimeMillis() + INTERVAL_MS)
        }

        /** Cancels the wakeup chain. Call from the driver's `onStop`. */
        fun stopAliveTask(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            // FLAG_NO_CREATE returns null when no alarm is pending, which is fine to ignore.
            val intent = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
            alarmManager.cancel(intent)
        }

        private fun schedule(context: Context, triggerAt: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
            // setExactAndAllowWhileIdle is the only variant that also fires in Doze, which is the
            // whole point of this receiver.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }

        private fun pendingIntent(context: Context, flags: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, AP550KeepAliveReceiver::class.java).apply { action = ACTION },
                flags or PendingIntent.FLAG_IMMUTABLE
            )
    }
}