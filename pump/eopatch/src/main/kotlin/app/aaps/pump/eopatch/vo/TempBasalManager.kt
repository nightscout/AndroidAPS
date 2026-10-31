package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.code.UnitOrPercent
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import com.google.common.base.Preconditions
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

class TempBasalManager : IPreference<TempBasalManager> {

    @Transient
    private val subject: BehaviorSubject<TempBasalManager> = BehaviorSubject.create()

    var startedBasal: TempBasal? = null

    var unit = UnitOrPercent.P

    fun clear() {
        startedBasal = null
    }

    /** Called when the patch confirmed the temp basal, so it started now. */
    fun updateBasalRunning(tempBasal: TempBasal) {
        Preconditions.checkNotNull(tempBasal)

        this.startedBasal = CommonUtils.clone(tempBasal)
        this.startedBasal?.running = true
        // The patch screen and the suspend dialog read the start from the temp basal. Only a field of this manager,
        // which nothing read, was set: a running temp basal showed "@01:00" (time 0) and no time left.
        this.startedBasal?.startTimestamp = System.currentTimeMillis()
    }

    fun updateBasalStopped() {
        this.startedBasal?.running = false
        this.startedBasal?.startTimestamp = 0
    }

    fun update(other: TempBasalManager) {
        this.startedBasal = other.startedBasal
        unit = other.unit
    }

    override fun observe(): Observable<TempBasalManager> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.TempBasal, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "TempBasalManager(startedBasal=$startedBasal, unit=$unit)"
    }
}
