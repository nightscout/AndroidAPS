package app.aaps.pump.eopatch.event

import app.aaps.core.interfaces.rx.events.Event
import app.aaps.pump.eopatch.alarm.AlarmCode

data class EventEoPatchAlarm(val alarmCodes: Set<AlarmCode>, val isFirst: Boolean = false) : Event()
class EventPatchActivationNotComplete : Event()
