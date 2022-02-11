package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.util.*

class Alarms(): IPreference<Alarms> {
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

    var occured = HashMap<AlarmCode, AlarmItem>()

    init {
        initObject()
    }

    fun initObject() {
    }

    fun clear(){
        registered.clear()
        occured.clear()
    }

    fun update(other: Alarms) {
        registered = other.registered
        occured = other.occured
    }

    fun register(alarmcode: AlarmCode, triggerAfter: Long) {
        val item = AlarmItem().apply {
            alarmCode = alarmcode
            createTimestamp = System.currentTimeMillis()
            triggerTimeMilli = createTimestamp + triggerAfter
        }
        if (isRegistered(alarmcode)){
            registered.remove(alarmcode)
        }
        registered.put(alarmcode, item)

    }

    fun unregister(alarmcode: AlarmCode) {
        if (isRegistered(alarmcode)){
            registered.remove(alarmcode)
        }
    }

    fun occured(alarmcode: AlarmCode) {
        val item: AlarmItem? = registered.get(alarmcode)
        if (!isOccuring(alarmcode) && item != null)
            occured.put(alarmcode, item)
        if (isRegistered(alarmcode))
            registered.remove(alarmcode)
    }

    fun handle(alarmcode: AlarmCode) {
        if (isOccuring(alarmcode))
            occured.remove(alarmcode)
    }

    fun isRegistered(alarmcode: AlarmCode): Boolean{
        return registered.containsKey(alarmcode)
    }

    fun isOccuring(alarmcode: AlarmCode): Boolean{
        return occured.containsKey(alarmcode)
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
        return "Alarms(subject=$subject, registered=${registered.keys}, occured=${occured.keys}"
    }

    companion object {
        const val NAME = "ALARMS"
        @JvmStatic
        fun createEmpty(): Alarms {
            return Alarms()
        }
    }

}
