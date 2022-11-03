package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.*

class Alarms: IPreference<Alarms> {
    @Transient
    private val subject: BehaviorSubject<Alarms> = BehaviorSubject.create()

    class AlarmItem {
        lateinit var alarmCode: AlarmCode
        var createTimestamp = 0L
        var triggerTimeMilli = 0L

        override fun toString(): String {
            return "AlarmItem(alarmCode=$alarmCode, createTimestamp=$createTimestamp, triggerTimeMilli=$triggerTimeMilli)"
        }
    }

    var registered = HashMap<AlarmCode, AlarmItem>()

    var occurred = HashMap<AlarmCode, AlarmItem>()

    init {
        initObject()
    }

    fun initObject() {
    }

    fun clear(){
        registered.clear()
        occurred.clear()
    }

    fun update(other: Alarms) {
        registered = other.registered
        occurred = other.occurred
    }

    fun register(alarmCode: AlarmCode, triggerAfter: Long) {
        val item = AlarmItem().apply {
            this.alarmCode = alarmCode
            createTimestamp = System.currentTimeMillis()
            triggerTimeMilli = createTimestamp + triggerAfter
        }
        if (isRegistered(alarmCode)){
            registered.remove(alarmCode)
        }
        registered.put(alarmCode, item)

    }

    fun unregister(alarmCode: AlarmCode) {
        if (isRegistered(alarmCode)){
            registered.remove(alarmCode)
        }
    }

    fun occurred(alarmCode: AlarmCode) {
        val item: AlarmItem? = registered.get(alarmCode)
        if (!isOccurring(alarmCode) && item != null)
            occurred.put(alarmCode, item)
        if (isRegistered(alarmCode))
            registered.remove(alarmCode)
    }

    fun handle(alarmCode: AlarmCode) {
        if (isOccurring(alarmCode))
            occurred.remove(alarmCode)
    }

    private fun isRegistered(alarmCode: AlarmCode): Boolean{
        return registered.containsKey(alarmCode)
    }

    fun isOccurring(alarmCode: AlarmCode): Boolean{
        return occurred.containsKey(alarmCode)
    }

    override fun observe(): Observable<Alarms> {
        return subject.hide()
    }

    override fun flush(sp: SP){
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        sp.putString(SettingKeys.ALARMS, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "Alarms(subject=$subject, registered=${registered.keys}, occurred=${occurred.keys}"
    }
}
