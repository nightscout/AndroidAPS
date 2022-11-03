package info.nightscout.androidaps.plugins.pump.eopatch

import android.content.Context
import android.content.Intent
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.Companion.fromIntent
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm

class OsAlarmReceiver : DaggerBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        fromIntent(intent)?.let { alarmCode ->
            EoPatchRxBus.publish(EventEoPatchAlarm(HashSet<AlarmCode>().apply { add(alarmCode) }))
        }
    }
}