package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import info.nightscout.androidaps.plugins.pump.eopatch.core.code.PatchAeCode
import io.reactivex.rxjava3.core.Maybe

interface IAlarmRegistry {

    fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean = false): Maybe<AlarmCode>
    fun add(patchAeCodes: Set<PatchAeCode>)
    fun remove(alarmCode: AlarmCode): Maybe<AlarmCode>
}