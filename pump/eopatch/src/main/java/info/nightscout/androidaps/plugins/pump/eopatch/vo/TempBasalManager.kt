package info.nightscout.androidaps.plugins.pump.eopatch.vo

import app.aaps.core.interfaces.sharedPreferences.SP
import com.google.common.base.Preconditions
import info.nightscout.androidaps.plugins.pump.eopatch.CommonUtils
import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.androidaps.plugins.pump.eopatch.code.UnitOrPercent
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

class TempBasalManager : IPreference<TempBasalManager> {

    @Transient
    private val subject: BehaviorSubject<TempBasalManager> = BehaviorSubject.create()

    var startedBasal: TempBasal? = null

    private var startTimestamp = 0L

    private var endTimestamp = 0L

    var unit = UnitOrPercent.P

    fun clear() {
        startedBasal = null
        startTimestamp = 0L
        endTimestamp = 0L
    }

    fun updateBasalRunning(tempBasal: TempBasal) {
        Preconditions.checkNotNull(tempBasal)

        this.startedBasal = CommonUtils.clone(tempBasal)
        this.startedBasal?.running = true
        this.startTimestamp = System.currentTimeMillis()
    }

    fun updateBasalStopped() {
        this.startedBasal?.running = false
        this.startedBasal?.startTimestamp = 0
    }

    fun update(other: TempBasalManager) {
        this.startedBasal = other.startedBasal
        startTimestamp = other.startTimestamp
        endTimestamp = other.endTimestamp
        unit = other.unit
    }

    override fun observe(): Observable<TempBasalManager> {
        return subject.hide()
    }

    override fun flush(sp: SP) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        sp.putString(SettingKeys.TEMP_BASAL, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "TempBasalManager(startedBasal=$startedBasal, startTimestamp=$startTimestamp, endTimestamp=$endTimestamp, unit=$unit)"
    }
}
