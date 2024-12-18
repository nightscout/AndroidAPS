package app.aaps.pump.eopatch

import android.content.Context
import android.content.Intent
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.AlarmCode.Companion.fromIntent
import app.aaps.pump.eopatch.event.EventEoPatchAlarm
import dagger.android.DaggerBroadcastReceiver

class OsAlarmReceiver : DaggerBroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        fromIntent(intent)?.let { alarmCode ->
            EoPatchRxBus.publish(EventEoPatchAlarm(HashSet<AlarmCode>().apply { add(alarmCode) }))
        }
    }
}