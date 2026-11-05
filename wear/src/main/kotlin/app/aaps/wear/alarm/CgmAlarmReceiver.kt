package app.aaps.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.di.injectMetroMembers
import dev.zacsweers.metro.Inject

/** Acknowledge on a CGM alarm notification of the watch, the button or a swipe. */
class CgmAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var cgmAlarmWatch: CgmAlarmWatch

    override fun onReceive(context: Context, intent: Intent) {
        context.injectMetroMembers(this)
        if (intent.action == ACTION_ACKNOWLEDGE) intent.getStringExtra(EXTRA_ID)?.let { cgmAlarmWatch.acknowledge(it) }
    }

    companion object {

        const val ACTION_ACKNOWLEDGE = "app.aaps.wear.cgmAlarm.ACKNOWLEDGE"
        const val EXTRA_ID = "cgmAlarmId"
    }
}
