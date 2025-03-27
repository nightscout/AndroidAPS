package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.util.FloatAdjusters
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

/**
 * 볼루스 주입 형태 2가지 모드
 *
 *
 * Bolus : '즉시 주입 볼루스' 와 '연장 주입 볼루스' 를 포함한 의미.
 * Now Bolus : '즉시 주입 볼루스' 를 의미.
 * Extended Bolus : '연장 주입 볼루스' 를 의미.
 *
 *
 * BolusCurrent : 현재 패치에서 진행 중인 '볼루스의 정보'를 표현한 클래스.
 */

class BolusCurrent : IPreference<BolusCurrent> {

    @Transient
    private val subject: BehaviorSubject<BolusCurrent> = BehaviorSubject.create()

    class Bolus {

        var historyId: Long = 0L

        var injected = 0f

        var remain = 0f

        var startTimestamp = 0L

        var endTimestamp = 0L

        // 즉시 주입 볼루스의 종료시간을 보정했는지 여부
        var endTimeSynced = false

        var duration = 0L

        val totalDoseU: Float
            get() = injected + remain

        fun startBolus(id: Long, targetDoseU: Float, start: Long, end: Long, duration: Long = 0L) {
            this.historyId = id
            this.injected = 0f
            this.remain = targetDoseU // 남은 양에 설정한다
            this.startTimestamp = start
            this.endTimestamp = end
            this.endTimeSynced = false
            this.duration = duration
        }

        fun clearBolus() {
            this.historyId = 0
            this.injected = 0f
            this.remain = 0f
            this.startTimestamp = 0
            this.endTimestamp = 0
            this.endTimeSynced = false
            this.duration = 0L
        }

        fun update(injected: Int, remain: Int) {
            this.injected = FloatAdjusters.FLOOR2_BOLUS.apply(injected * AppConstant.INSULIN_UNIT_P)
            this.remain = FloatAdjusters.FLOOR2_BOLUS.apply(remain * AppConstant.INSULIN_UNIT_P)
        }

        fun updateTimeStamp(start: Long, end: Long) {
            this.startTimestamp = start
            this.endTimestamp = end
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Bolus

            if (historyId != other.historyId) return false
            if (injected != other.injected) return false
            if (remain != other.remain) return false
            if (startTimestamp != other.startTimestamp) return false
            if (endTimestamp != other.endTimestamp) return false
            return endTimeSynced == other.endTimeSynced
        }

        override fun hashCode(): Int {
            var result = historyId.hashCode()
            result = 31 * result + injected.hashCode()
            result = 31 * result + remain.hashCode()
            result = 31 * result + startTimestamp.hashCode()
            result = 31 * result + endTimestamp.hashCode()
            result = 31 * result + endTimeSynced.hashCode()
            return result
        }

        override fun toString(): String =
            when (historyId) {
                0L   -> "Bolus(NONE)"
                else -> "Bolus(id=$historyId, i=$injected, r=$remain, start=$startTimestamp, end=$endTimestamp, synced=$endTimeSynced)"
            }
    }

    var nowBolus: Bolus = Bolus()
    var extBolus: Bolus = Bolus()

    private fun getBolus(type: BolusType): Bolus =
        when (type) {
            BolusType.NOW -> nowBolus
            BolusType.EXT -> extBolus
            else          -> nowBolus
        }

    fun historyId(t: BolusType) = getBolus(t).historyId
    fun injected(t: BolusType) = getBolus(t).injected
    fun remain(t: BolusType) = getBolus(t).remain
    fun startTimestamp(t: BolusType) = getBolus(t).startTimestamp
    fun endTimestamp(t: BolusType) = getBolus(t).endTimestamp
    fun endTimeSynced(t: BolusType) = getBolus(t).endTimeSynced
    fun totalDoseU(t: BolusType) = getBolus(t).totalDoseU
    fun duration(t: BolusType) = getBolus(t).duration

    fun clearBolus(t: BolusType) = getBolus(t).clearBolus()

    fun clearAll() {
        clearBolus(BolusType.NOW)
        clearBolus(BolusType.EXT)
    }

    fun setEndTimeSynced(t: BolusType, synced: Boolean) {
        getBolus(t).endTimeSynced = synced
    }

    fun startNowBolus(nowHistoryId: Long, targetDoseU: Float, startTimestamp: Long, endTimestamp: Long) {
        nowBolus.startBolus(nowHistoryId, targetDoseU, startTimestamp, endTimestamp)
    }

    fun startExtBolus(exHistoryId: Long, targetDoseU: Float, startTimestamp: Long, endTimestamp: Long, duration: Long) {
        extBolus.startBolus(exHistoryId, targetDoseU, startTimestamp, endTimestamp, duration)
    }

    fun updateBolusFromPatch(type: BolusType, injected: Int, remain: Int) {
        getBolus(type).update(injected, remain)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BolusCurrent

        if (nowBolus != other.nowBolus) return false
        return extBolus == other.extBolus
    }

    override fun observe(): Observable<BolusCurrent> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.BolusCurrent, jsonStr)
        subject.onNext(this)
    }

    override fun hashCode(): Int {
        var result = nowBolus.hashCode()
        result = 31 * result + extBolus.hashCode()
        return result
    }

    override fun toString(): String {
        return "BolusCurrent(nowBolus=$nowBolus, extBolus=$extBolus)"
    }
}
