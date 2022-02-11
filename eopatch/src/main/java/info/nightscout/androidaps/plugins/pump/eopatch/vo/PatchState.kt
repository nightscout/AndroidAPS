package info.nightscout.androidaps.plugins.pump.eopatch.vo

import android.os.Build
import android.util.Base64
import com.google.gson.stream.JsonWriter
import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant
import info.nightscout.androidaps.plugins.pump.eopatch.GsonHelper
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType
import info.nightscout.androidaps.plugins.pump.eopatch.core.util.FloatAdjusters
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.stream.IntStream
import kotlin.math.roundToInt

class PatchState: IPreference<PatchState> {
    @Transient
    private val subject: BehaviorSubject<PatchState> = BehaviorSubject.create()

    val stateBytes: ByteArray
    var updatedTimestamp: Long = 0

    constructor(): this(ByteArray(SIZE), 0) {
    }

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

    fun clear(){
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

    /**
     * 참고표
     * 0 = 0x01
     * 1 = 0x02
     * 2 = 0x04
     * 3 = 0x08
     * 4 = 0x10
     * 5 = 0x20
     * 6 = 0x40
     * 7 = 0x80
     */
    private fun bitwiseAnd(value: Byte, bit: Int): Int {
        return value.toInt() and (1 shl bit)
    }

    val isNeedSyncTime: Boolean
        get() = getBoolean(D3, 0)
    val isNeedPriming: Boolean
        get() = getBoolean(D3, 1)
    val isNeedNeedleSensing: Boolean
        get() = getBoolean(D3, 2)

    fun useEncryption(): Boolean {
        return getBoolean(D3, 3)
    }

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

    fun _isExtBolusInjecting(): Boolean {
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
        if((get(D7) + 145) > 300) return 100

        return ((get(D7) + 145 - 210) * 100.0 / 90).roundToInt()
    }

    fun bootCount(): Int {
        return get(D8)
    }

    fun aeCount(): Int {
        return get(D11)
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
                    remainedPumpCycle * AppConstant.INSULIN_UNIT_P)
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

    /*
        템프베이젤 Active(동작) 상태
         - tempBasalReg:1, tempBasalAct:1, tempBasalDone:0
        템프베이젤 No Active (정지) 상태
         - tempBasalReg:0, tempBasalAct:0, tempBasalDone:0
         - tempBasalReg:1, tempBasalAct:0, tempBasalDone:1
     */
    val isTempBasalActive: Boolean
        get() = isTempBasalReg && isTempBasalAct && !isTempBasalDone

    /*
        Bolus
     */
    private val isRecentPatchState: Boolean
        private get() = System.currentTimeMillis() - updatedTimestamp < UPDATE_CONNECTION_INTERVAL_MILLI
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
            BolusType.NOW -> isNowBolusRegAct
            BolusType.EXT -> isExtBolusRegAct
            BolusType.COMBO -> isNowBolusRegAct && isExtBolusRegAct
            else            -> isNowBolusRegAct && isExtBolusRegAct
        }
    }

    fun isBolusDone(type: BolusType?): Boolean {
        return when (type) {
            BolusType.NOW -> isNowBolusDone
            BolusType.EXT -> isExtBolusDone
            BolusType.COMBO -> isNowBolusDone || isExtBolusDone
            else            -> isNowBolusDone || isExtBolusDone
        }
    }

    val isExtBolusInjectionWaiting: Boolean
        get() = isExtBolusActive && !_isExtBolusInjecting()
    val isExtBolusInjecting: Boolean
        get() = isExtBolusActive && _isExtBolusInjecting()
    val isBolusNotActive: Boolean
        get() = !isBolusActive

    // 0 이면 reset
    val isResetAutoOffTime: Boolean
        get() = stateBytes[D3].toInt() and 0x80 == 0

    private fun b(value: Boolean): String {
        return if (value) ON else "  "
    }

    /**
     * toString 심플 버전.
     */
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
            Arrays.asList(indent, "isPatchInternalSuspended:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNeedPriming) {
            Arrays.asList(indent, "NeedPriming:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNeedNeedleSensing) {
            Arrays.asList(indent, "NeedNeedleSensing:", ON).forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isNowBolusRegAct || isNowBolusDone) {
            Arrays.asList(
                indent, "[NowBolus] RegAct:", b(isNowBolusRegAct), "  Done:", b(isNowBolusDone))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isExtBolusRegAct || isExtBolusDone || isExtBolusTime || _isExtBolusInjecting()) {
            Arrays.asList(
                indent, "[ExtBolus] RegAct:", b(isExtBolusRegAct), "  Done:", b(isExtBolusDone), "  Time:", b(isExtBolusTime), "  Injecting:", b(_isExtBolusInjecting()))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        if (isTempBasalReg || isTempBasalAct || isTempBasalDone) {
            Arrays.asList(
                indent, "[TempBasal] Reg:", b(isTempBasalReg), "  Act:", b(isTempBasalAct), "  Done:", b(isTempBasalDone))
                .forEach(Consumer { str: String? -> sb.append(str) })
        }
        Arrays.asList(
            indent, "[NormalBasal] Reg:", b(isNormalBasalReg), "  Act:", b(isNormalBasalAct), "  Paused:", b(isNormalBasalPaused),
            indent, "remainedInsulin:", remainedInsulin(), "  remainedPumpCycle:", remainedPumpCycle(), "(", remainedInsulin, ")", "  battery:", battery())
            .forEach(Consumer<Serializable> { obj: Serializable? -> sb.append(obj) })
        return sb.toString()
    }

    fun info(): String {
        val sb = StringBuilder()
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        sb.append("\n업데이트 된 시간\n(패치 시간 아니에요)")
        sb.append("""
    
    ${format.format(updatedTimestamp)}
    
    """.trimIndent())
        if (isCriticalAlarm || isNewAlertAlarm) {
            sb.append(String.format("%nAlarm: %s %s",
                if (isCriticalAlarm) "Critical" else "",
                if (isNewAlertAlarm) "Alert" else ""))
        }
        sb.append("""
    
    GlobalTime: ${convertHumanTimeWithStandard(currentTime())}
    """.trimIndent())
        sb.append("\nNeedPriming: $isNeedPriming")
        sb.append("\nNeedNeedleSensing: $isNeedNeedleSensing")
        sb.append("\nPrimingSuccess: $isPrimingSuccess")
        sb.append("\nBasalReg: $isNormalBasalReg")
        sb.append("\nTempBasalReg: $isTempBasalReg")
        sb.append("\nBasalAct: $isNormalBasalAct")
        sb.append("\nisNowBolusRegAct: $isNowBolusRegAct")
        sb.append("\nisNowBolusDone: $isNowBolusDone")
        sb.append("\nisExtBolusRegAct: $isExtBolusRegAct")
        sb.append("\nisExtBolusDone: $isExtBolusDone")
        sb.append("\nisNormalBasalReg: $isNormalBasalReg")
        sb.append("\nisNormalBasalAct: $isNormalBasalAct")
        sb.append("\nisNormalBasalPaused: $isNormalBasalPaused")
        sb.append("\nisTempBasalReg: $isTempBasalReg")
        sb.append("\nisTempBasalAct: $isTempBasalAct")
        sb.append("\nisTempBasalDone: $isTempBasalDone")
        sb.append("\nExBolusTime: $isExtBolusTime")
        sb.append("\nPumpAct: $isPumpAct")
        sb.append("""
    
    remainedInsulin: ${remainedInsulin()}
    """.trimIndent())
        sb.append("""
    
    remainedPumpCycle:${remainedPumpCycle()}($remainedInsulin)
    """.trimIndent())
        sb.append("""
    
    boot count :${bootCount()}
    """.trimIndent())
        sb.append("""
    
    aeCount : ${aeCount()}
    """.trimIndent())
        sb.append("""
    
    runningTime : ${runningTime()}hr
    """.trimIndent())
        sb.append("\nisPumpInternalSuspended : $isPatchInternalSuspended")
        sb.append("\nisResetAutoOffTime : $isResetAutoOffTime")
        sb.append("\n\n\n")
        return sb.toString()
    }

    fun convertHumanTimeWithStandard(timeSec: Int): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        calendar.timeInMillis = TimeUnit.SECONDS.toMillis(timeSec.toLong())
        return dateFormat.format(calendar.time)
    }

    /**
     * 다른 PatchState 와 비교해서 같은 값인지 확인.
     * API 키[0-1], FUNC[2], 시간[14-17] 은 비교하지 않음.
     * @param other 비교할 PatchState
     * @return 같으면 true 다르면 false.
     */
    fun equalState(other: PatchState): Boolean {
        return IntStream.of(D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13, D18, D19)
            .allMatch { i: Int -> other.stateBytes[i] == stateBytes[i] }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as PatchState
        return Arrays.equals(stateBytes, that.stateBytes)
    }

    override fun observe(): Observable<PatchState> {
        return subject.hide()
    }

    override fun flush(sp: SP){
        val jsonStr = GsonHelper.sharedGson().toJson(this)
        sp.putString(SettingKeys.PATCH_STATE, jsonStr)
        subject.onNext(this)
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(stateBytes)
    }

    @Throws(IOException::class) fun writeJson(out: JsonWriter) {
        out.beginObject()
        out.name(NAME)
        out.beginObject()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            out.name("stateBytes").value(java.util.Base64.getEncoder().encodeToString(stateBytes))
        } else {
            out.name("stateBytes").value(Arrays.toString(Base64.encode(stateBytes,
                Base64.DEFAULT)))
        }
        out.name("updateTimestamp").value(updatedTimestamp)
        out.endObject()
        out.endObject()
    }

    companion object {

        val UPDATE_CONNECTION_INTERVAL_MILLI = TimeUnit.SECONDS.toMillis(10)
        const val NAME = "PATCH_STATE"
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

        private const val D0 = 0 // DUMMY
        private const val D1 = 1 // DUMMY
        private const val D2 = 2 // FUNCTION CODE 0x22
        private const val D3 = 3 // SETUP
        private const val D4 = D3 + 1 // BOLUS
        private const val D5 = D3 + 2 // ALERT
        private const val D6 = D3 + 3 // INSULIN
        private const val D7 = D3 + 4 // BATTERY
        private const val D8 = D3 + 5 // BOOT
        private const val D9 = D3 + 6 // APS
        private const val D10 = D3 + 7 // APS
        private const val D11 = D3 + 8 // New Alarm Count
        private const val D12 = D3 + 9 // Remain Pump Count
        private const val D13 = D3 + 10 // Remain Pump Count
        private const val D14 = D3 + 11 // Current Time
        private const val D18 = D3 + 15 // Remained Insulin
        private const val D19 = D3 + 16 // Running Time
    }
}