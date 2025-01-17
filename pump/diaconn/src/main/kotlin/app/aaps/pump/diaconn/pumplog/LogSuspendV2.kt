package app.aaps.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 일시정지 시작 (기저정지)
*/
@Suppress("SpellCheckingInspection")
class LogSuspendV2 private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    val batteryRemain: Byte,    // 1=기본, 2=생활1, 3=생활2, 4=생활3, 5=닥터1, 6=닥터2
    private val patternType: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_SUSPEND_V2{")
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

    fun getBasalPattern(): String {
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

        const val LOG_KIND: Byte = 0x03
        fun parse(data: String): LogSuspendV2 {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogSuspendV2(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}