package info.nightscout.androidaps.plugins.pump.eopatch.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasalManager
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Observable
import javax.inject.Inject
import javax.inject.Singleton

interface IPreferenceManager {

    fun getPatchConfig(): PatchConfig
    fun getPatchState(): PatchState
    fun getBolusCurrent(): BolusCurrent
    fun getNormalBasalManager(): NormalBasalManager
    fun getTempBasalManager(): TempBasalManager
    fun getAlarms(): Alarms
    fun init()
    fun flushPatchConfig()
    fun flushPatchState()
    fun flushBolusCurrent()
    fun flushNormalBasalManager()
    fun flushTempBasalManager()
    fun flushAlarms()
    fun updatePatchLifeCycle(event: PatchLifecycleEvent)
    fun updatePatchState(newState: PatchState)
    fun getPatchSerial(): String
    fun getPatchMac(): String?
    fun isActivated(): Boolean
    fun setMacAddress(mac: String)
    fun getPatchExpiredTime(): Long
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

/**
 * patch2 패키지에서 사용하는 프리퍼런스의 작업을 대신 처리하는 클래스
 */
@Singleton
class PreferenceManager @Inject constructor() : IPreferenceManager {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var mPatchConfig: PatchConfig
    @Inject lateinit var mNormalBasalMgr: NormalBasalManager
    @Inject lateinit var mTempBasalMgr: TempBasalManager
    @Inject lateinit var mAlarms: Alarms

    private var mPatchState = PatchState()
    private var mBolusCurrent = BolusCurrent()
    private lateinit var observePatchLifeCycle: Observable<PatchLifecycle>
    private var initialized = false

    @Inject
    fun onInit() {
        observePatchLifeCycle = mPatchConfig.observe()
            .map { patchConfig -> patchConfig.lifecycleEvent.lifeCycle }
            .distinctUntilChanged()
            .replay(1).refCount()
    }

    override fun getPatchConfig(): PatchConfig {
        return mPatchConfig
    }

    override fun getPatchState(): PatchState {
        return mPatchState
    }

    override fun getBolusCurrent(): BolusCurrent {
        return mBolusCurrent
    }

    override fun getNormalBasalManager(): NormalBasalManager {
        return mNormalBasalMgr
    }

    override fun getTempBasalManager(): TempBasalManager {
        return mTempBasalMgr
    }

    override fun getAlarms(): Alarms {
        return mAlarms
    }

    override fun init() {
        try {
            val jsonStr = sp.getString(SettingKeys.PATCH_STATE, "")
            val savedState = GsonHelper.sharedGson().fromJson(jsonStr, PatchState::class.java)
            mPatchState = savedState
        } catch (ex: Exception) {
            mPatchState = PatchState()
            aapsLogger.error(LTag.PUMP, ex.message ?: "PatchState load error")
        }

        try {
            val jsonStr = sp.getString(SettingKeys.BOLUS_CURRENT, "")
            val savedBolusCurrent = GsonHelper.sharedGson().fromJson(jsonStr, BolusCurrent::class.java)
            mBolusCurrent = savedBolusCurrent
        } catch (ex: Exception) {
            mBolusCurrent = BolusCurrent()
            aapsLogger.error(LTag.PUMP, ex.message ?: "BolusCurrent load error")
        }

        try {
            val jsonStr = sp.getString(SettingKeys.PATCH_CONFIG, "")
            val savedConfig = GsonHelper.sharedGson().fromJson(jsonStr, PatchConfig::class.java)
            mPatchConfig.update(savedConfig)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "PatchConfig load error")
        }

        try {
            val jsonStr = sp.getString(SettingKeys.NORMAL_BASAL, "")
            val normalBasalManager = GsonHelper.sharedGson().fromJson(jsonStr, NormalBasalManager::class.java)
            mNormalBasalMgr.update(normalBasalManager)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "NormalBasal load error")
        }

        try {
            val jsonStr = sp.getString(SettingKeys.TEMP_BASAL, "")
            val tempBasalManager = GsonHelper.sharedGson().fromJson(jsonStr, TempBasalManager::class.java)
            mTempBasalMgr.update(tempBasalManager)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "TempBasal load error")
        }

        try {
            val jsonStr = sp.getString(SettingKeys.ALARMS, "")
            val alarms = GsonHelper.sharedGson().fromJson(jsonStr, Alarms::class.java)
            mAlarms.update(alarms)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMP, ex.message ?: "Alarms load error")
        }

        aapsLogger.info(LTag.PUMP, "Load from PatchConfig preference: $mPatchConfig")
        aapsLogger.info(LTag.PUMP, "Load from PatchState preference: $mPatchState")
        aapsLogger.info(LTag.PUMP, "Load from BolusCurrent preference: $mBolusCurrent")
        aapsLogger.info(LTag.PUMP, "Load from NormalBasal preference: $mNormalBasalMgr")
        aapsLogger.info(LTag.PUMP, "Load from TempBasal preference: $mTempBasalMgr")
        aapsLogger.info(LTag.PUMP, "Load from Alarms preference: $mAlarms")
        initialized = true
    }

    override fun isInitDone() = initialized

    override fun flushPatchConfig() = mPatchConfig.flush(sp)
    override fun flushPatchState() = mPatchState.flush(sp)
    override fun flushBolusCurrent() = mBolusCurrent.flush(sp)
    override fun flushNormalBasalManager() = mNormalBasalMgr.flush(sp)
    override fun flushTempBasalManager() = mTempBasalMgr.flush(sp)
    override fun flushAlarms() = mAlarms.flush(sp)

    @Synchronized
    override fun updatePatchLifeCycle(event: PatchLifecycleEvent) {
        mPatchConfig.updateLifecycle(event)
        flushPatchConfig()

        when (event.lifeCycle) {
            PatchLifecycle.SHUTDOWN -> {
                mPatchState.clear()
                flushPatchState()
                mBolusCurrent.clearAll()
                flushBolusCurrent()
                mTempBasalMgr.clear()
                flushTempBasalManager()
            }

            else                    -> Unit
        }

    }

    override fun updatePatchState(newState: PatchState) {
        mPatchState = newState
        flushPatchState()
    }

    override fun getPatchSerial(): String {
        return mPatchConfig.patchSerialNumber
    }

    override fun getPatchMac(): String? {
        return mPatchConfig.macAddress
    }

    override fun isActivated(): Boolean {
        return mPatchConfig.isActivated
    }

    override fun setMacAddress(mac: String) {
        mPatchConfig.macAddress = mac
        flushPatchConfig()
    }

    override fun getPatchExpiredTime(): Long {
        return mPatchConfig.getPatchExpiredTime()
    }

    override fun setSharedKey(bytes: ByteArray?) {
        mPatchConfig.sharedKey = bytes
    }

    override fun setSeq15(seq15: Int) {
        mPatchConfig.seq15 = seq15
    }

    override fun getSeq15(): Int {
        return mPatchConfig.seq15
    }

    override fun increaseSeq15() {
        mPatchConfig.incSeq()
    }

    override fun getPatchWakeupTimestamp(): Long {
        return mPatchConfig.patchWakeupTimestamp
    }

    override fun observePatchLifeCycle(): Observable<PatchLifecycle> {
        return observePatchLifeCycle
    }

    override fun observePatchConfig(): Observable<PatchConfig> {
        return mPatchConfig.observe()
    }

    override fun observePatchState(): Observable<PatchState> {
        return mPatchState.observe()
    }

    override fun observeBolusCurrent(): Observable<BolusCurrent> {
        return mBolusCurrent.observe()
    }

    override fun observeAlarm(): Observable<Alarms> {
        return mAlarms.observe()
    }
}