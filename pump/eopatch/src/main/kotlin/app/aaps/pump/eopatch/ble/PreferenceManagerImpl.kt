package app.aaps.pump.eopatch.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.BolusCurrent
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * patch2 패키지에서 사용하는 프리퍼런스의 작업을 대신 처리하는 클래스
 */
@Singleton
class PreferenceManagerImpl @Inject constructor() : PreferenceManager {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var patchConfig: PatchConfig
    @Inject lateinit var normalBasalMgr: NormalBasalManager
    @Inject lateinit var tempBasalManager: TempBasalManager
    @Inject lateinit var mAlarms: Alarms

    override var patchState = PatchState()
    override var bolusCurrent = BolusCurrent()
    private lateinit var observePatchLifeCycle: Observable<PatchLifecycle>
    private var initialized = false

    @Inject
    fun onInit() {
        observePatchLifeCycle = patchConfig.observe()
            .map { patchConfig -> patchConfig.lifecycleEvent.lifeCycle }
            .distinctUntilChanged()
            .replay(1).refCount()
    }

    override fun init() {
        try {
            val jsonStr = preferences.get(EopatchStringNonKey.PatchState)
            val savedState = GsonHelper.sharedGson().fromJson(jsonStr, PatchState::class.java)
            patchState = savedState
        } catch (ex: Exception) {
            patchState = PatchState()
            aapsLogger.error(LTag.PUMP, ex.message ?: "PatchState load error")
        }

        try {
            val jsonStr = preferences.get(EopatchStringNonKey.BolusCurrent)
            val savedBolusCurrent = GsonHelper.sharedGson().fromJson(jsonStr, BolusCurrent::class.java)
            bolusCurrent = savedBolusCurrent
        } catch (ex: Exception) {
            bolusCurrent = BolusCurrent()
            aapsLogger.error(LTag.PUMP, ex.message ?: "BolusCurrent load error")
        }

        try {
            val jsonStr = preferences.get(EopatchStringNonKey.PatchConfig)
            val savedConfig = GsonHelper.sharedGson().fromJson(jsonStr, PatchConfig::class.java)
            patchConfig.update(savedConfig)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "PatchConfig load error")
        }

        try {
            val jsonStr = preferences.get(EopatchStringNonKey.NormalBasal)
            val normalBasalManager = GsonHelper.sharedGson().fromJson(jsonStr, NormalBasalManager::class.java)
            normalBasalMgr.update(normalBasalManager)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "NormalBasal load error")
        }

        try {
            val jsonStr = preferences.get(EopatchStringNonKey.TempBasal)
            val tempBasalManager = GsonHelper.sharedGson().fromJson(jsonStr, TempBasalManager::class.java)
            tempBasalManager.update(tempBasalManager)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "TempBasal load error")
        }

        try {
            val jsonStr = preferences.get(EopatchStringNonKey.Alarms)
            val alarms = GsonHelper.sharedGson().fromJson(jsonStr, Alarms::class.java)
            mAlarms.update(alarms)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "Alarms load error")
        }

        aapsLogger.info(LTag.PUMP, "Load from PatchConfig preference: $patchConfig")
        aapsLogger.info(LTag.PUMP, "Load from PatchState preference: $patchState")
        aapsLogger.info(LTag.PUMP, "Load from BolusCurrent preference: $bolusCurrent")
        aapsLogger.info(LTag.PUMP, "Load from NormalBasal preference: $normalBasalMgr")
        aapsLogger.info(LTag.PUMP, "Load from TempBasal preference: $tempBasalManager")
        aapsLogger.info(LTag.PUMP, "Load from Alarms preference: $mAlarms")
        initialized = true
    }

    override fun isInitDone() = initialized

    override fun flushPatchConfig() = patchConfig.flush(preferences)
    override fun flushPatchState() = patchState.flush(preferences)
    override fun flushBolusCurrent() = bolusCurrent.flush(preferences)
    override fun flushNormalBasalManager() = normalBasalMgr.flush(preferences)
    override fun flushTempBasalManager() = tempBasalManager.flush(preferences)
    override fun flushAlarms() = mAlarms.flush(preferences)

    @Synchronized
    override fun updatePatchLifeCycle(event: PatchLifecycleEvent) {
        patchConfig.updateLifecycle(event)
        flushPatchConfig()

        when (event.lifeCycle) {
            PatchLifecycle.SHUTDOWN -> {
                patchState.clear()
                flushPatchState()
                bolusCurrent.clearAll()
                flushBolusCurrent()
                tempBasalManager.clear()
                flushTempBasalManager()
            }

            else                    -> Unit
        }

    }

    override fun updatePatchState(newState: PatchState) {
        patchState = newState
        flushPatchState()
    }

    override fun setMacAddress(mac: String) {
        patchConfig.macAddress = mac
        flushPatchConfig()
    }

    override fun setSharedKey(bytes: ByteArray?) {
        patchConfig.sharedKey = bytes
    }

    override fun setSeq15(seq15: Int) {
        patchConfig.seq15 = seq15
    }

    override fun getSeq15(): Int {
        return patchConfig.seq15
    }

    override fun increaseSeq15() {
        patchConfig.incSeq()
    }

    override fun getPatchWakeupTimestamp(): Long {
        return patchConfig.patchWakeupTimestamp
    }

    override fun observePatchLifeCycle(): Observable<PatchLifecycle> {
        return observePatchLifeCycle
    }

    override fun observePatchConfig(): Observable<PatchConfig> {
        return patchConfig.observe()
    }

    override fun observePatchState(): Observable<PatchState> {
        return patchState.observe()
    }

    override fun observeBolusCurrent(): Observable<BolusCurrent> {
        return bolusCurrent.observe()
    }

    override fun observeAlarm(): Observable<Alarms> {
        return mAlarms.observe()
    }
}