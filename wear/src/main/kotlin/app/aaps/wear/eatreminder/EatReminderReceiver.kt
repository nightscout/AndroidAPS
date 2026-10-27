package app.aaps.wear.eatreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject

/** The alarm of the "time to eat" timer, and the Silence and +5 min buttons of its notification. */
class EatReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var eatReminderWatch: EatReminderWatch

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        when (intent.action) {
            ACTION_RING   -> eatReminderWatch.ring()
            ACTION_STOP   -> eatReminderWatch.silence()
            ACTION_EXTEND -> eatReminderWatch.extend()
        }
    }

    companion object {

        const val ACTION_RING = "app.aaps.wear.eatReminder.RING"
        const val ACTION_STOP = "app.aaps.wear.eatReminder.STOP"
        const val ACTION_EXTEND = "app.aaps.wear.eatReminder.EXTEND"
    }
}
