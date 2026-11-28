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
            ACTION_REPEAT       -> phoneAlarmWatch.repeat()
            ACTION_SILENCE      -> phoneAlarmWatch.silence()
            ACTION_MUTE         -> phoneAlarmWatch.mute()
            ACTION_SWIPE        -> phoneAlarmWatch.swipe()
            ACTION_PHONE_BUTTON -> phoneAlarmWatch.runAction(intent.getIntExtra(EXTRA_INDEX, 0))
        }
    }

    companion object {

        const val ACTION_REPEAT = "app.aaps.wear.phoneAlarm.REPEAT"
        const val ACTION_SILENCE = "app.aaps.wear.phoneAlarm.SILENCE"
        const val ACTION_MUTE = "app.aaps.wear.phoneAlarm.MUTE"

        /** The notification was swiped away (the delete intent). Its own action, so the log tells it from a button. */
        const val ACTION_SWIPE = "app.aaps.wear.phoneAlarm.SWIPE"

        /** A button of the phone's own notification, as a Nightscout snooze. [EXTRA_INDEX] says which. */
        const val ACTION_PHONE_BUTTON = "app.aaps.wear.phoneAlarm.PHONE_BUTTON"
        const val EXTRA_INDEX = "index"
    }
}
