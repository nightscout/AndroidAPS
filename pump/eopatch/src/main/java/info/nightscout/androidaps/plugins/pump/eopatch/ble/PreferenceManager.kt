package info.nightscout.androidaps.plugins.pump.eopatch.ble

import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Observable

interface PreferenceManager {

    val patchState: PatchState
    var bolusCurrent: BolusCurrent
    fun init()
    fun flushPatchConfig()
    fun flushPatchState()
    fun flushBolusCurrent()
    fun flushNormalBasalManager()
    fun flushTempBasalManager()
    fun flushAlarms()
    fun updatePatchLifeCycle(event: PatchLifecycleEvent)
    fun updatePatchState(newState: PatchState)
    fun setMacAddress(mac: String)
    fun setSharedKey(bytes: ByteArray?)
    fun setSeq15(seq15: Int)
    fun getSeq15(): Int
    fun increaseSeq15()
    fun getPatchWakeupTimestamp(): Long
    fun observePatchLifeCycle(): Observable<PatchLifecycle>
    fun observePatchConfig(): Observable<PatchConfig>
    fun observePatchState(): Observable<PatchState>
    fun observeBolusCurrent(): Observable<BolusCurrent>
    fun observeAlarm(): Observable<Alarms>
    fun isInitDone(): Boolean
}

