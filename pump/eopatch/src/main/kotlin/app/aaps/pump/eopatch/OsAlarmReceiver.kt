package app.aaps.pump.eopatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.AlarmCode.Companion.fromIntent
import app.aaps.pump.eopatch.event.EventEoPatchAlarm

/**
 * A plain [BroadcastReceiver]: it has no injected fields, so it needs no injector.
 */
class OsAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        fromIntent(intent)?.let { alarmCode ->
            EoPatchRxBus.publish(EventEoPatchAlarm(HashSet<AlarmCode>().apply { add(alarmCode) }))
        }
    }
}
