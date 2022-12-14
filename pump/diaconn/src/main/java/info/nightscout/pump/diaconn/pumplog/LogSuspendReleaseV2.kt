package info.nightscout.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 일시정지 중지 (기저정지 해제)
*/
@Suppress("SpellCheckingInspection")
class LogSuspendReleaseV2 private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    batteryRemain: Byte,
    patternType: Byte
) {

    val type: Byte
    val kind: Byte
    val batteryRemain: Byte
    private val patternType // 1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2
        : Byte

    override fun toString(): String {
        val sb = StringBuilder("LOG_SUSPEND_RELEASE_V2{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append(", patternType=").append(patternType.toInt())
        sb.append('}')
        return sb.toString()
    }

    fun getBasalPattern():String {
        //1=Injection blockage, 2=Battery shortage, 3=Drug shortage, 4=User shutdown, 5=System reset, 6=Other, 7=Emergency shutdown
        return when(patternType) {
            1.toByte() -> "Base"
            2.toByte() -> "Life1"
            3.toByte() -> "Life2"
            4.toByte() -> "Life3"
            5.toByte() -> "Dr1"
            6.toByte() -> "Dr2"
            else -> "No Pattern"
        }
    }
    companion object {

        const val LOG_KIND: Byte = 0x04
        fun parse(data: String): LogSuspendReleaseV2 {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogSuspendReleaseV2(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

    init {
        type = PumpLogUtil.getType(typeAndKind)
        kind = PumpLogUtil.getKind(typeAndKind)
        this.batteryRemain = batteryRemain
        this.patternType = patternType
    }
}