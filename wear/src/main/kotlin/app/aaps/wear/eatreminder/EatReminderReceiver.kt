package app.aaps.wear.eatreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject

/** The alarm of the "time to eat" timer, the buttons of its notifications, and the swipe that dismisses the running one. */
class EatReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var eatReminderWatch: EatReminderWatch

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        when (intent.action) {
            ACTION_RING   -> eatReminderWatch.ring()
            ACTION_STOP   -> eatReminderWatch.silence()
            ACTION_EXTEND -> eatReminderWatch.extend()
            ACTION_REPOST -> eatReminderWatch.repost()
        }
    }

    companion object {

        const val ACTION_RING = "app.aaps.wear.eatReminder.RING"
        const val ACTION_STOP = "app.aaps.wear.eatReminder.STOP"
        const val ACTION_EXTEND = "app.aaps.wear.eatReminder.EXTEND"
        const val ACTION_REPOST = "app.aaps.wear.eatReminder.REPOST"
    }
}
