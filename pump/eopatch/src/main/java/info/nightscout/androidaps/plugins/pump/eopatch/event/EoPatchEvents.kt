package info.nightscout.androidaps.plugins.pump.eopatch.event

import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode
import info.nightscout.rx.events.Event

class EventEoPatchAlarm(var alarmCodes: Set<AlarmCode>, var isFirst: Boolean = false) : Event()
class EventDialog(val dialog: DialogFragment, val show: Boolean) : Event()
class EventProgressDialog(val show: Boolean, @StringRes val resId: Int = 0) : Event()
class EventPatchActivationNotComplete : Event()