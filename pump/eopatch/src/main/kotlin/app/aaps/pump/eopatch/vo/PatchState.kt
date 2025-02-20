package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.GsonHelper
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.util.FloatAdjusters
import app.aaps.pump.eopatch.keys.EopatchStringNonKey
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.IntStream

class PatchState : IPreference<PatchState> {

    @Transient
    private val subject: BehaviorSubject<PatchState> = BehaviorSubject.create()

    private val stateBytes: ByteArray
    var updatedTimestamp: Long = 0

    constructor() : this(ByteArray(SIZE), 0)

    constructor(stateBytes: ByteArray, updatedTimestamp: Long) {
        this.stateBytes = stateBytes
        this.updatedTimestamp = updatedTimestamp
    }

    fun update(newValue: ByteArray, timestamp: Long) {
        if (newValue.size == 17) {
            stateBytes[D0] = 0x00
            stateBytes[D1] = 0x00
            stateBytes[D2] = 0x22
            System.arraycopy(newValue, 0, stateBytes, 3, 17)
        } else {
            System.arraycopy(newValue, 0, stateBytes, 0, stateBytes.size)
        }
        updatedTimestamp = timestamp
        subject.onNext(this)
    }

    fun clear() {
        update(ByteArray(SIZE), 0)
    }

    fun update(other: PatchState) {
        if (other.stateBytes.size == 17) {
            stateBytes[D0] = 0x00
            stateBytes[D1] = 0x00
            stateBytes[D2] = 0x22
            System.arraycopy(other.stateBytes, 0, stateBytes, 3, 17)
        } else {
            System.arraycopy(other.stateBytes, 0, stateBytes, 0, stateBytes.size)
        }
        updatedTimestamp = other.updatedTimestamp
        subject.onNext(this)
    }

    val isEmpty: Boolean
        get() = updatedTimestamp == 0L

    private fun get(index: Int): Int {
        return stateBytes[index].toInt() and 0xFF
    }

    private fun getBoolean(index: Int, bit: Int): Boolean {
        return bitwiseAnd(stateBytes[index], bit) != 0
    }

    private fun bitwiseAnd(value: Byte, bit: Int): Int {
        return value.toInt() and (1 shl bit)
    }

    val isNeedPriming: Boolean
        get() = getBoolean(D3, 1)
    val isNeedNeedleSensing: Boolean
        get() = getBoolean(D3, 2)

    val isPrimingSuccess: Boolean
        get() = getBoolean(D3, 6)

    fun primingState(): Int {
        return stateBytes[D3].toInt() and 0x70 shr 4
    }

    val isNowBolusRegAct: Boolean
        get() = getBoolean(D4, 0)
    val isExtBolusRegAct: Boolean
        get() = getBoolean(D4, 1)
    val isNormalBasalReg: Boolean
        get() = getBoolean(D4, 2)
    val isTempBasalReg: Boolean
        get() = getBoolean(D4, 3)
    val isNormalBasalAct: Boolean
        get() = getBoolean(D4, 4)
    val isTempBasalAct: Boolean
        get() = getBoolean(D4, 5)

    fun isExtBolusInjecting(): Boolean {
        return getBoolean(D4, 6)
    }

    val isNewAlertAlarm: Boolean
        get() = getBoolean(D5, 0)
    val isCriticalAlarm: Boolean
        get() = getBoolean(D5, 7)

    val isPumpAct: Boolean
        get() = getBoolean(D6, 0)
    val isPatchInternalSuspended: Boolean
        get() = getBoolean(D6, 2)
    val isNowBolusDone: Boolean
        get() = getBoolean(D6, 4)

    val isExtBolusTime: Boolean
        get() = getBoolean(D6, 5)
    val isExtBolusDone: Boolean
        get() = getBoolean(D6, 6)
    val isTempBasalDone: Boolean
        get() = getBoolean(D6, 7)

    fun battery(): String {
        return String.format("%.2fV", (get(D7) + 145) / 100.0f)
    }

    fun batteryLevel(): Int {
        val volt = (get(D7) + 145) * 10
        val batteryLevel: Int
        if (volt >= 3000) {
            batteryLevel = 100
        } else if (volt > 2900) {
            batteryLevel = 100 - ((3000 - volt) * 10) / 100
        } else if (volt > 2740) {
            batteryLevel = 90 - ((2900 - volt) * 20) / 160
        } else if (volt > 2440) {
            batteryLevel = 70 - ((2740 - volt) * 50) / 300
        } else if (volt > 2100) {
            batteryLevel = 20 - ((2440 - volt) * 20) / 340
        } else {
            batteryLevel = 0
        }

        return batteryLevel
    }

    //==============================================================================================
    // PUMP COUNT
    //==============================================================================================
    private fun remainedPumpCycle(): Int {
        return stateBytes[D12].toInt() and 0xFF shl 8 or (stateBytes[D12 + 1].toInt() and 0xFF)
    }

    //==============================================================================================
    // CURRENT TIME (TimeUnit.SECOND)
    //==============================================================================================
    fun currentTime(): Int {
        return byteToInt(stateBytes, D14)
    }

    //==============================================================================================
    // REMAINED INSULIN
    //==============================================================================================
    private fun remainedInsulin(): Int {
        return get(D18)
    }

    val remainedInsulin: Float
        get() {
            val remainedPumpCycle = remainedPumpCycle()
            return if (remainedPumpCycle > 0) {
                FloatAdjusters.FLOOR2_INSULIN.apply(
                    remainedPumpCycle * AppConstant.INSULIN_UNIT_P
                )
            } else {
                remainedInsulin().toFloat()
            }
        }

    //==============================================================================================
    // RUNNING TIME
    //==============================================================================================
    fun runningTime(): Int {
        return get(D19)
    }

    //==============================================================================================
    // Helper methods
    //==============================================================================================
    val isNormalBasalPaused: Boolean
        get() = isNormalBasalReg && !isNormalBasalAct
    val isNormalBasalRunning: Boolean
        get() = isNormalBasalReg && isNormalBasalAct

    val isTempBasalActive: Boolean
        get() = isTempBasalReg && isTempBasalAct && !isTempBasalDone

    /*
        Bolus
     */
    val isBolusActive: Boolean
        get() = isNowBolusActive || isExtBolusActive
    val isNowBolusActive: Boolean
        get() = isNowBolusRegAct && !isNowBolusDone
    val isNowBolusFinished: Boolean
        get() = isNowBolusRegAct && isNowBolusDone
    val isExtBolusActive: Boolean
        get() = isExtBolusRegAct && !isExtBolusDone
    val isExtBolusFinished: Boolean
        get() = isExtBolusRegAct && isExtBolusDone

    fun isBolusActive(type: BolusType?): Boolean {
        return when (type) {
            BolusType.NOW   -> isNowBolusRegAct
            BolusType.EXT   -> isExtBolusRegAct
            BolusType.COMBO -> isNowBolusRegAct && isExtBolusRegAct
            else            -> isNowBolusRegAct && isExtBolusRegAct
        }
    }

    fun isBolusDone(type: BolusType?): Boolean {
        return when (type) {
            BolusType.NOW   -> isNowBolusDone
            BolusType.EXT   -> isExtBolusDone
            BolusType.COMBO -> isNowBolusDone || isExtBolusDone
            else            -> isNowBolusDone || isExtBolusDone
        }
    }

    private fun b(value: Boolean): String {
        return if (value) ON else "  "
    }

    fun t(): String {
        return "PatchState{" + convertHumanTimeWithStandard(currentTime()) + "}"
    }

    override fun toString(): String {
        val sb = StringBuilder()
        val indent = " "
        sb.append("PatchState")
        if (isCriticalAlarm || isNewAlertAlarm) {
            sb.append(indent).append("#### Error:")
                .append(if (isCriticalAlarm) "Critical" else "")
                .append(if (isNewAlertAlarm) "Alert" else "")
        }
        sb.append(indent).append("GlobalTime:").append(convertHumanTimeWithStandard(currentTime()))
        sb.append(" --> ")
        IntStream.of(D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13, D18)
            .forEach { i: Int -> sb.append(String.format(" %02X ", stateBytes[i])) }
        if (isPatchInternalSuspended) {
            listOf(indent, "isPatchInternalSuspended:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNeedPriming) {
            listOf(indent, "NeedPriming:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNeedNeedleSensing) {
            listOf(indent, "NeedNeedleSensing:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNowBolusRegAct || isNowBolusDone) {
            listOf(indent, "[NowBolus] RegAct:", b(isNowBolusRegAct), "  Done:", b(isNowBolusDone))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isExtBolusRegAct || isExtBolusDone || isExtBolusTime || isExtBolusInjecting()) {
            listOf(indent, "[ExtBolus] RegAct:", b(isExtBolusRegAct), "  Done:", b(isExtBolusDone), "  Time:", b(isExtBolusTime), "  Injecting:", b(isExtBolusInjecting()))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isTempBasalReg || isTempBasalAct || isTempBasalDone) {
            listOf(indent, "[TempBasal] Reg:", b(isTempBasalReg), "  Act:", b(isTempBasalAct), "  Done:", b(isTempBasalDone))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        listOf(
            indent, "[NormalBasal] Reg:", b(isNormalBasalReg), "  Act:", b(isNormalBasalAct), "  Paused:", b(isNormalBasalPaused),
            indent, "remainedInsulin:", remainedInsulin(), "  remainedPumpCycle:", remainedPumpCycle(), "(", remainedInsulin, ")", "  battery:", battery()
        )
            .forEach(Consumer<Serializable> { obj: Serializable? -> sb.append(obj) })
        return sb.toString()
    }

    fun convertHumanTimeWithStandard(timeSec: Int): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        calendar.timeInMillis = TimeUnit.SECONDS.toMillis(timeSec.toLong())
        return dateFormat.format(calendar.time)
    }

    fun equalState(other: PatchState): Boolean {
        return IntStream.of(D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13, D18, D19)
            .allMatch { i: Int -> other.stateBytes[i] == stateBytes[i] }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as PatchState
        return stateBytes.contentEquals(that.stateBytes)
    }

    override fun observe(): Observable<PatchState> {
        return subject.hide()
    }

    override fun flush(preferences: Preferences) {
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        preferences.put(EopatchStringNonKey.PatchState, jsonStr)
        subject.onNext(this)
    }

    override fun hashCode(): Int {
        return stateBytes.contentHashCode()
    }

    companion object {

        const val SIZE = 20
        @JvmStatic fun create(bytes: ByteArray?, updatedTimestamp: Long): PatchState {
            var stateBytes = bytes
            if (stateBytes == null || stateBytes.size < SIZE) {
                stateBytes = ByteArray(SIZE)
            }
            stateBytes[D0] = 0x00
            stateBytes[D1] = 0x00
            stateBytes[D2] = 0x22
            return PatchState(stateBytes, updatedTimestamp)
        }

        private const val ON = "On"

        private fun byteToInt(bs: ByteArray, startPos: Int): Int {
            return bs[startPos + 0].toInt() and 0xFF shl 24 or (bs[startPos + 1].toInt() and 0xFF shl 16) or (bs[startPos + 2].toInt() and 0xFF shl 8) or (bs[startPos + 3].toInt() and 0xFF)
        }

        private const val D0 = 0
        private const val D1 = 1
        private const val D2 = 2
        private const val D3 = 3
        private const val D4 = D3 + 1
        private const val D5 = D3 + 2
        private const val D6 = D3 + 3
        private const val D7 = D3 + 4
        private const val D8 = D3 + 5
        private const val D9 = D3 + 6
        private const val D10 = D3 + 7
        private const val D11 = D3 + 8
        private const val D12 = D3 + 9
        private const val D13 = D3 + 10
        private const val D14 = D3 + 11
        private const val D18 = D3 + 15
        private const val D19 = D3 + 16
    }
}