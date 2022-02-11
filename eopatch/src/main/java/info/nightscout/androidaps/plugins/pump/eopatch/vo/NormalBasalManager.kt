package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.plugins.pump.eopatch.CommonUtils
import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.androidaps.plugins.pump.eopatch.code.BasalStatus
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class NormalBasalManager() : IPreference<NormalBasalManager> {
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

    fun isEqual(profile: Profile?): Boolean{
        if(profile == null) false

        if(profile?.getBasalValues()?.size?:0 != normalBasal.list.size) false

        return profile?.let{prof ->
            for(i in prof.getBasalValues().indices){
                if(TimeUnit.SECONDS.toMinutes(prof.getBasalValues()[i].timeAsSeconds.toLong()) != normalBasal.list.get(i).start){
                    return false
                }
                if(CommonUtils.nearlyNotEqual(prof.getBasalValues()[i].value.toFloat(), normalBasal.list.get(i).doseUnitPerHour, 0.0000001f)){
                    return false
                }
            }
            return true
        }?:false
    }

    fun convertProfileToNormalBasal(profile: Profile): NormalBasal {
        val tmpNormalBasal = NormalBasal()
        tmpNormalBasal.list.clear()

        val size = profile.getBasalValues().size
        for(idx in profile.getBasalValues().indices){
            val next_idx = if(idx == (size - 1)) 0 else idx + 1
            val st_mins = TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[idx].timeAsSeconds.toLong())
            val et_mins = if(next_idx == 0) 1440 else TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[next_idx].timeAsSeconds.toLong())

            tmpNormalBasal.list.add(BasalSegment(st_mins, et_mins, profile.getBasalValues()[idx].value.toFloat()))
        }

        return tmpNormalBasal
    }

    fun setNormalBasal(profile: Profile) {
        normalBasal.list.clear()

        val size = profile.getBasalValues().size
        for(idx in profile.getBasalValues().indices){
            val next_idx = if(idx == (size - 1)) 0 else idx + 1
            val st_mins = TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[idx].timeAsSeconds.toLong())
            val et_mins = if(next_idx == 0) 1440 else TimeUnit.SECONDS.toMinutes(profile.getBasalValues()[next_idx].timeAsSeconds.toLong())

            normalBasal.list.add(BasalSegment(st_mins, et_mins, profile.getBasalValues()[idx].value.toFloat()))
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
    fun updateBasalSelecteded(index: Int) {
        normalBasal.status = BasalStatus.SELECTED
    }

    @Synchronized
    fun updateBasalSelecteded() {
        normalBasal.status = BasalStatus.SELECTED
    }

    fun updateForDeactivation() {
        // deactivation 할때는 SELECTED 상태로 변경
        updateBasalSelecteded()
    }

    fun update(other: NormalBasalManager){
        normalBasal = other.normalBasal
    }

    override fun observe(): Observable<NormalBasalManager> {
        return subject.hide()
    }

    override fun flush(sp: SP){
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        sp.putString(SettingKeys.NORMAL_BASAL, jsonStr)
        subject.onNext(this)
    }


    override fun toString(): String {
        return "NormalBasalManager(normalBasal=$normalBasal)"
    }

}
