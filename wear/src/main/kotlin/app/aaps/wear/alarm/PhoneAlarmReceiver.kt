package app.aaps.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject

/** The buttons and the swipe of the phone alarm's notification, and its repeats. */
class PhoneAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var phoneAlarmWatch: PhoneAlarmWatch

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        when (intent.action) {
            ACTION_REPEAT  -> phoneAlarmWatch.repeat()
            ACTION_SILENCE -> phoneAlarmWatch.silence()
            ACTION_MUTE    -> phoneAlarmWatch.mute()
        }
    }

    companion object {

        const val ACTION_REPEAT = "app.aaps.wear.phoneAlarm.REPEAT"
        const val ACTION_SILENCE = "app.aaps.wear.phoneAlarm.SILENCE"
        const val ACTION_MUTE = "app.aaps.wear.phoneAlarm.MUTE"
    }
}
