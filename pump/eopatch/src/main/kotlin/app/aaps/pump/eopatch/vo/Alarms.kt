package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

class Alarms : IPreference<Alarms> {

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

    var needToStopBeep = HashSet<AlarmCode>()

    init {
        initObject()
    }

    fun initObject() {
    }

    fun clear() {
        registered.clear()
        occurred.clear()
        needToStopBeep.clear()
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
        if (isRegistered(alarmCode)) {
            registered.remove(alarmCode)
        }
        registered.put(alarmCode, item)

    }

    fun unregister(alarmCode: AlarmCode) {
        if (isRegistered(alarmCode)) {
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

    fun getOccuredAlarmTimestamp(alarmCode: AlarmCode): Long {
        return if (occurred.containsKey(alarmCode))
            occurred.getValue(alarmCode).triggerTimeMilli
        else
            System.currentTimeMillis()
    }

    private fun isRegistered(alarmCode: AlarmCode): Boolean {
        return registered.containsKey(alarmCode)
    }

    fun isOccurring(alarmCode: AlarmCode): Boolean {
        return occurred.containsKey(alarmCode)
    }

    override fun observe(): Observable<Alarms> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.Alarms, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "Alarms(subject=$subject, registered=${registered.keys}, occurred=${occurred.keys}"
    }
}
