package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.CommonUtils
import app.aaps.pump.eopatch.FloatFormatters
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.core.define.IPatchConstant.WARRANTY_OPERATING_LIFE_MILLI
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import com.google.android.gms.common.internal.Preconditions
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

// @Singleton
class PatchConfig : IPreference<PatchConfig> {

    @Transient
    private val subject: BehaviorSubject<PatchConfig> = BehaviorSubject.create()
    var securityValue: ByteArray = byteArrayOf(0, 0)

    var macAddress: String? = null
    var lifecycleEvent: PatchLifecycleEvent = PatchLifecycleEvent()
    var bolusNormalStartTimestamp = 0L
    var bolusNormalEndTimestamp = 0L
    var bolusNormalDoseU = 0f
    var bolusExStartTimestamp = 0L
    var bolusExEndTimestamp = 0L
    var bolusExDoseU = 0f
    var injectCount = 0
    var bgReminderMinute = 0L
    var lastIndex = 0
    var lastDisconnectedTimestamp = 0L // 마지막 연결 종료 시간

    var standardBolusInjectCount = 0
    var extendedBolusInjectCount = 0
    var basalInjectCount = 0

    var patchFirmwareVersion: String? = null
    var patchSerialNumber: String = ""
    var patchLotNumber: String? = null
    var patchModelName: String? = null

    var patchWakeupTimestamp = 0L
        set(wakeupTimestamp) {
            field = wakeupTimestamp
            expireDurationMilli = WARRANTY_OPERATING_LIFE_MILLI
        }

    var activatedTimestamp = 0L // 최초 연결 시간
    var expireDurationMilli = 0L // 패치 만료 기간 , 3일 (wake-up 시간을 기준으로 3일)
    var basalPauseFinishTimestamp = 0L //  베이젤 일시중지 만료 시간
    var needleInsertionTryCount = 0 // 바늘삽입 시도 횟수

    /* 패치와 API 통신으로 업데이트 값을 여기에 기록 중복 API 호출이 생기면 안되는 경우 여기에 */
    // SET_LOW_RESERVOIR_TASK
    var lowReservoirAlertAmount = 10
    var patchExpireAlertTime = 4
    var infoReminder = false

    var pumpDurationSmallMilli = 0L // small
        get(): Long = if (field != 0L) field else AppConstant.PUMP_DURATION_MILLI
    var pumpDurationMediumMilli = 0L // medium
        get(): Long = if (field != 0L) field else AppConstant.PUMP_DURATION_MILLI
    var pumpDurationLargeMilli = 0L // large
        get(): Long = if (field != 0L) field else AppConstant.PUMP_DURATION_MILLI
    //var pumpDurationOcclusion = 0L // occul, 사용안함

    var isEnterPrimaryScreen = false

    // 기초 프로그램 변경시 BLE로 패치에 보내야 하기 때문에 마크한다.
    var needSetBasalSchedule = false

    var sharedKey: ByteArray? = null
    var seq15: Int = -1

    var rotateKnobNeedleSensingError = false

    var remainedInsulin = 0f

    //wake-up 시간을 기준으로 3.5일
    val expireTimestamp: Long
        get() = patchWakeupTimestamp + expireDurationMilli

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expireTimestamp

    val isActivated: Boolean
        get() = this.lifecycleEvent.isActivated

    val isSubStepRunning: Boolean
        get() = this.lifecycleEvent.isSubStepRunning

    val isBasalSetting: Boolean
        get() = this.lifecycleEvent.isBasalSetting

    val isDeactivated: Boolean
        get() = !hasMacAddress()

    val isInBasalPausedTime: Boolean
        get() = this.basalPauseFinishTimestamp > 0 && basalPauseFinishTimestamp > System.currentTimeMillis()

    val insulinInjectionAmount: Float
        get() = injectCount * AppConstant.INSULIN_UNIT_P

    val insulinInjectionAmountStr: String
        get() = FloatFormatters.insulin(injectCount * AppConstant.INSULIN_UNIT_P, "U")

    val bolusInjectionAmount: Float
        get() = (standardBolusInjectCount + extendedBolusInjectCount) * AppConstant.INSULIN_UNIT_P

    val basalInjectionAmount: Float
        get() = basalInjectCount * AppConstant.INSULIN_UNIT_P

    init {
        initObject()
    }

    fun initObject() {
        this.lifecycleEvent = PatchLifecycleEvent()
        this.lastIndex = 0
    }

    fun updateDeactivated() {
        this.macAddress = null
        this.patchFirmwareVersion = null
        this.patchSerialNumber = ""
        this.patchLotNumber = null
        this.patchWakeupTimestamp = 0
        this.activatedTimestamp = 0
        this.expireDurationMilli = 0
        this.lifecycleEvent = PatchLifecycleEvent()
        this.needleInsertionTryCount = 0
        this.bolusNormalStartTimestamp = 0
        this.bolusNormalEndTimestamp = 0
        this.bolusExStartTimestamp = 0
        this.bolusExEndTimestamp = 0
        this.injectCount = 0
        this.lastIndex = 0
        this.pumpDurationSmallMilli = 0
        this.pumpDurationMediumMilli = 0
        this.pumpDurationLargeMilli = 0
        this.needSetBasalSchedule = false
        this.sharedKey = null
        this.seq15 = -1
        this.standardBolusInjectCount = 0
        this.extendedBolusInjectCount = 0
        this.basalInjectCount = 0
        this.remainedInsulin = 0f
    }

    fun patchFirmwareVersionString(): String? {
        patchFirmwareVersion?.let {
            var count = 0
            var i = 0
            while (i < it.length) {
                if (it[i] == '.') {
                    count++
                    if (count == 3) {
                        return it.substring(0, i)
                    }
                }
                i++
            }
        }

        return patchFirmwareVersion
    }

    val patchExpiredTime: Long get() = if (isActivated) expireTimestamp else -1L

    @Synchronized
    fun incSeq() {
        if (seq15 >= 0) {
            seq15++
        }
        if (seq15 > 0x7FFF) {
            seq15 = 0
        }
    }

    fun updateLifecycle(event: PatchLifecycleEvent) {
        Preconditions.checkNotNull(event)

        /* 마지막 이력을 기록해 두어야 알람이 재발생하는 것을 막아야 함 */
        if (event.lifeCycle == lifecycleEvent.lifeCycle) {
            return
        }

        this.lifecycleEvent = event
        when (event.lifeCycle) {
            PatchLifecycle.SHUTDOWN               -> {
                updateDeactivated()
            }

            PatchLifecycle.BONDED                 -> {
            }

            PatchLifecycle.SAFETY_CHECK           -> {
            }

            PatchLifecycle.REMOVE_NEEDLE_CAP      -> {
            }

            PatchLifecycle.REMOVE_PROTECTION_TAPE -> {
            }

            PatchLifecycle.ROTATE_KNOB            -> {
            }

            PatchLifecycle.BASAL_SETTING          -> {
            }

            PatchLifecycle.ACTIVATED              -> {
                // updateFirstConnected 이 부분으로 옮김.
                this.activatedTimestamp = System.currentTimeMillis()
                //this.expireDurationMilli = WARRANTY_OPERATING_LIFE_MILLI
                //this.patchWakeupTimestamp = 0 getwakeuptime response 로 업데이트 됨.
                this.needleInsertionTryCount = 0
                this.bolusNormalStartTimestamp = 0
                this.bolusNormalEndTimestamp = 0
                this.bolusExStartTimestamp = 0
                this.bolusExEndTimestamp = 0
                this.lastIndex = 0
                this.needSetBasalSchedule = false
            }
        }
    }

    fun updateNormalBasalPaused(pauseDurationHour: Float) {
        Preconditions.checkArgument(pauseDurationHour == 0.5f || pauseDurationHour == 1.0f || pauseDurationHour == 1.5f || pauseDurationHour == 2.0f)
        this.basalPauseFinishTimestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis((pauseDurationHour * 60).toLong())
    }

    fun updateNormalBasalPausedSilently() {
        this.basalPauseFinishTimestamp = 0L
    }

    fun updateNormalBasalResumed() {
        this.basalPauseFinishTimestamp = 0
    }

    fun updateNormalBasalStarted() {
        this.basalPauseFinishTimestamp = 0
    }

    fun updateTempBasalStarted() {
        this.basalPauseFinishTimestamp = 0
    }

    fun hasMacAddress(): Boolean {
        return CommonUtils.hasText(macAddress)
    }

    fun updatetDisconnectedTime() {
        this.lastDisconnectedTimestamp = System.currentTimeMillis()
    }

    fun update(other: PatchConfig) {
        macAddress = other.macAddress
        lifecycleEvent = other.lifecycleEvent
        bolusNormalStartTimestamp = other.bolusNormalStartTimestamp
        bolusNormalEndTimestamp = other.bolusNormalEndTimestamp
        bolusNormalDoseU = other.bolusNormalDoseU
        bolusExStartTimestamp = other.bolusExStartTimestamp
        bolusExEndTimestamp = other.bolusExEndTimestamp
        bolusExDoseU = other.bolusExDoseU
        bgReminderMinute = other.bgReminderMinute
        lastIndex = other.lastIndex
        lastDisconnectedTimestamp = other.lastDisconnectedTimestamp
        patchFirmwareVersion = other.patchFirmwareVersion
        patchSerialNumber = other.patchSerialNumber
        patchLotNumber = other.patchLotNumber
        patchWakeupTimestamp = other.patchWakeupTimestamp
        activatedTimestamp = other.activatedTimestamp
        expireDurationMilli = other.expireDurationMilli
        basalPauseFinishTimestamp = other.basalPauseFinishTimestamp
        needleInsertionTryCount = other.needleInsertionTryCount
        isEnterPrimaryScreen = other.isEnterPrimaryScreen
        needSetBasalSchedule = other.needSetBasalSchedule
        sharedKey = other.sharedKey
        seq15 = other.seq15
        patchModelName = other.patchModelName
        needleInsertionTryCount = other.needleInsertionTryCount
        injectCount = other.injectCount
        pumpDurationSmallMilli = other.pumpDurationSmallMilli
        pumpDurationMediumMilli = other.pumpDurationMediumMilli
        pumpDurationLargeMilli = other.pumpDurationLargeMilli
        standardBolusInjectCount = other.standardBolusInjectCount
        extendedBolusInjectCount = other.extendedBolusInjectCount
        basalInjectCount = other.basalInjectCount
        lowReservoirAlertAmount = other.lowReservoirAlertAmount
        patchExpireAlertTime = other.patchExpireAlertTime
        remainedInsulin = other.remainedInsulin

        subject.onNext(this)
    }

    override fun observe(): Observable<PatchConfig> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.PatchConfig, jsonStr)
        subject.onNext(this)
    }

    override fun toString(): String {
        return "PatchConfig(securityValue=${securityValue.contentToString()}, macAddress=$macAddress, lifecycleEvent=$lifecycleEvent, bolusNormalStartTimestamp=$bolusNormalStartTimestamp, bolusNormalEndTimestamp=$bolusNormalEndTimestamp, bolusNormalDoseU=$bolusNormalDoseU, bolusExStartTimestamp=$bolusExStartTimestamp, bolusExEndTimestamp=$bolusExEndTimestamp, bolusExDoseU=$bolusExDoseU, injectCount=$injectCount, bgReminderMinute=$bgReminderMinute, lastIndex=$lastIndex, lastDisconnectedTimestamp=$lastDisconnectedTimestamp, standardBolusInjectCount=$standardBolusInjectCount, extendedBolusInjectCount=$extendedBolusInjectCount, basalInjectCount=$basalInjectCount, patchFirmwareVersion=$patchFirmwareVersion, patchSerialNumber='$patchSerialNumber', patchLotNumber=$patchLotNumber, patchModelName=$patchModelName, patchWakeupTimestamp=$patchWakeupTimestamp, activatedTimestamp=$activatedTimestamp, expireDurationMilli=$expireDurationMilli, basalPauseFinishTimestamp=$basalPauseFinishTimestamp, needleInsertionTryCount=$needleInsertionTryCount, LowReservoirAlertAmount=$lowReservoirAlertAmount, patchExpireAlertTime=$patchExpireAlertTime, isEnterPrimaryScreen=$isEnterPrimaryScreen, needSetBasalSchedule=$needSetBasalSchedule, sharedKey=${sharedKey?.contentToString()}, seq15=$seq15, rotateKnobNeedleSensingError=$rotateKnobNeedleSensingError, remainedInsulin=$remainedInsulin)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PatchConfig

        if (!securityValue.contentEquals(other.securityValue)) return false
        if (macAddress != other.macAddress) return false
        if (lifecycleEvent != other.lifecycleEvent) return false
        if (bolusNormalStartTimestamp != other.bolusNormalStartTimestamp) return false
        if (bolusNormalEndTimestamp != other.bolusNormalEndTimestamp) return false
        if (bolusNormalDoseU != other.bolusNormalDoseU) return false
        if (bolusExStartTimestamp != other.bolusExStartTimestamp) return false
        if (bolusExEndTimestamp != other.bolusExEndTimestamp) return false
        if (bolusExDoseU != other.bolusExDoseU) return false
        if (injectCount != other.injectCount) return false
        if (bgReminderMinute != other.bgReminderMinute) return false
        if (lastIndex != other.lastIndex) return false
        if (lastDisconnectedTimestamp != other.lastDisconnectedTimestamp) return false
        if (standardBolusInjectCount != other.standardBolusInjectCount) return false
        if (extendedBolusInjectCount != other.extendedBolusInjectCount) return false
        if (basalInjectCount != other.basalInjectCount) return false
        if (patchFirmwareVersion != other.patchFirmwareVersion) return false
        if (patchSerialNumber != other.patchSerialNumber) return false
        if (patchLotNumber != other.patchLotNumber) return false
        if (patchModelName != other.patchModelName) return false
        if (patchWakeupTimestamp != other.patchWakeupTimestamp) return false
        if (activatedTimestamp != other.activatedTimestamp) return false
        if (expireDurationMilli != other.expireDurationMilli) return false
        if (basalPauseFinishTimestamp != other.basalPauseFinishTimestamp) return false
        if (needleInsertionTryCount != other.needleInsertionTryCount) return false
        if (lowReservoirAlertAmount != other.lowReservoirAlertAmount) return false
        if (patchExpireAlertTime != other.patchExpireAlertTime) return false
        if (isEnterPrimaryScreen != other.isEnterPrimaryScreen) return false
        if (needSetBasalSchedule != other.needSetBasalSchedule) return false
        if (sharedKey != null) {
            if (other.sharedKey == null) return false
            if (!sharedKey.contentEquals(other.sharedKey)) return false
        } else if (other.sharedKey != null) return false
        if (seq15 != other.seq15) return false
        if (rotateKnobNeedleSensingError != other.rotateKnobNeedleSensingError) return false
        return remainedInsulin == other.remainedInsulin
    }

    override fun hashCode(): Int {
        var result = securityValue.contentHashCode()
        result = 31 * result + (macAddress?.hashCode() ?: 0)
        result = 31 * result + lifecycleEvent.hashCode()
        result = 31 * result + bolusNormalStartTimestamp.hashCode()
        result = 31 * result + bolusNormalEndTimestamp.hashCode()
        result = 31 * result + bolusNormalDoseU.hashCode()
        result = 31 * result + bolusExStartTimestamp.hashCode()
        result = 31 * result + bolusExEndTimestamp.hashCode()
        result = 31 * result + bolusExDoseU.hashCode()
        result = 31 * result + injectCount
        result = 31 * result + bgReminderMinute.hashCode()
        result = 31 * result + lastIndex
        result = 31 * result + lastDisconnectedTimestamp.hashCode()
        result = 31 * result + standardBolusInjectCount
        result = 31 * result + extendedBolusInjectCount
        result = 31 * result + basalInjectCount
        result = 31 * result + (patchFirmwareVersion?.hashCode() ?: 0)
        result = 31 * result + patchSerialNumber.hashCode()
        result = 31 * result + (patchLotNumber?.hashCode() ?: 0)
        result = 31 * result + (patchModelName?.hashCode() ?: 0)
        result = 31 * result + patchWakeupTimestamp.hashCode()
        result = 31 * result + activatedTimestamp.hashCode()
        result = 31 * result + expireDurationMilli.hashCode()
        result = 31 * result + basalPauseFinishTimestamp.hashCode()
        result = 31 * result + needleInsertionTryCount
        result = 31 * result + lowReservoirAlertAmount
        result = 31 * result + patchExpireAlertTime
        result = 31 * result + isEnterPrimaryScreen.hashCode()
        result = 31 * result + needSetBasalSchedule.hashCode()
        result = 31 * result + (sharedKey?.contentHashCode() ?: 0)
        result = 31 * result + seq15
        result = 31 * result + rotateKnobNeedleSensingError.hashCode()
        result = 31 * result + remainedInsulin.hashCode()
        return result
    }
}
