package app.aaps.pump.eopatch.vo

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.code.BasalStatus
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class NormalBasalManager : IPreference<NormalBasalManager> {

    @Transient
    private val subject: BehaviorSubject<NormalBasalManager> = BehaviorSubject.create()

    var normalBasal: NormalBasal = NormalBasal()

    val isStarted: Boolean
        get() = normalBasal.status.isStarted

    init {
        initObject()
    }

    fun initObject() {
    }

    fun isEqual(profile: Profile?): Boolean {
        return profile?.let {
            if (it.getBasalValues().size != normalBasal.list.size)
                return false

            for (i in it.getBasalValues().indices) {
                if (TimeUnit.SECONDS.toMinutes(it.getBasalValues()[i].timeAsSeconds.toLong()) != normalBasal.list[i].start) {
                    return false
                }
                if (!CommonUtils.nearlyEqual(
                        it.getBasalValues()[i].value.toFloat(), normalBasal
                            .list[i].doseUnitPerHour, 0.0000001f
                    )
                ) {
                    return false
                }
            }
            return true
        } ?: false
    }

    fun convertProfileToNormalBasal(profile: Profile): NormalBasal {
        val tmpNormalBasal = NormalBasal()
        tmpNormalBasal.list.clear()

        val size = profile.getBasalValues().size
        for (idx in profile.getBasalValues().indices) {
            val nextIdx = if (idx == (size - 1)) 0 else idx + 1
            val startTimeMinutes = TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[idx].timeAsSeconds.toLong())
            val endTimeMinutes = if (nextIdx == 0) 1440 else TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[nextIdx].timeAsSeconds.toLong())

            tmpNormalBasal.list.add(BasalSegment(startTimeMinutes, endTimeMinutes, profile.getBasalValues()[idx].value.toFloat()))
        }

        return tmpNormalBasal
    }

    fun setNormalBasal(profile: Profile) {
        normalBasal.list.clear()

        val size = profile.getBasalValues().size
        for (idx in profile.getBasalValues().indices) {
            val nextIdx = if (idx == (size - 1)) 0 else idx + 1
            val startTimeMinutes = TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[idx].timeAsSeconds.toLong())
            val endTimeMinutes = if (nextIdx == 0) 1440 else TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[nextIdx].timeAsSeconds.toLong())

            normalBasal.list.add(BasalSegment(startTimeMinutes, endTimeMinutes, profile.getBasalValues()[idx].value.toFloat()))
        }
    }

    @Synchronized
    fun updateBasalStarted() {
        normalBasal.status = BasalStatus.STARTED
    }

    @Synchronized
    fun updateBasalPaused() {
        normalBasal.status = BasalStatus.PAUSED
    }

    @Synchronized
    fun updateBasalSuspended() {
        normalBasal.status = BasalStatus.SUSPENDED
    }

    @Synchronized
    fun isSuspended(): Boolean {
        return normalBasal.status == BasalStatus.SUSPENDED
    }

    @Synchronized
    fun updateBasalSelected() {
        normalBasal.status = BasalStatus.SELECTED
    }

    fun updateForDeactivation() {
        // deactivation 할때는 SELECTED 상태로 변경
        updateBasalSelected()
    }

    fun update(other: NormalBasalManager) {
        normalBasal = other.normalBasal
    }

    override fun observe(): Observable<NormalBasalManager> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.NormalBasal, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "NormalBasalManager(normalBasal=$normalBasal)"
    }

}
