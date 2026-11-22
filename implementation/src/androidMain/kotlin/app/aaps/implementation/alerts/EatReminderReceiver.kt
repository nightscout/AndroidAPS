package app.aaps.implementation.alerts

import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.workflow.MetroBroadcastReceiver
import dev.zacsweers.metro.Inject

/**
 * The Stop and +5 min buttons of the "time to eat" countdown notification ([ACTION_STOP], [ACTION_EXTEND]). Its alarm
 * at the end is a booked alert now (`_docs/ALARMS.md`, step 1e), which the core raises; it used to come here too.
 *
 * A manifest receiver is not injected by the system, so it asks the graph itself, like `AlarmMuteReceiver`.
 */
class EatReminderReceiver : MetroBroadcastReceiver() {

    @Inject lateinit var eatReminder: EatReminder
    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        aapsLogger.debug(LTag.AUTOMATION, "EatReminderReceiver: ${intent.action}")
        when (intent.action) {
            ACTION_STOP   -> eatReminder.stop()
            ACTION_EXTEND -> eatReminder.extend()
        }
    }

    companion object {

        const val ACTION_STOP = "app.aaps.implementation.eatReminder.STOP"
        const val ACTION_EXTEND = "app.aaps.implementation.eatReminder.EXTEND"

        /** Separate request codes, so the buttons' intents do not replace each other. */
        fun requestCodeOf(action: String): Int = when (action) {
            ACTION_STOP -> 4721
            else        -> 4722
        }
    }
}
