package app.aaps.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 바늘 공기빼기 성공
*/
@Suppress("SpellCheckingInspection")
class LogChangeNeedleSuccess private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    //  47.5=4750
    val primeAmount: Short,    //  47.5=4750
    val remainAmount: Short,
    val batteryRemain: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_CHANGE_NEEDLE_SUCCESS{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", primeAmount=").append(primeAmount.toInt())
        sb.append(", remainAmount=").append(remainAmount.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x1C
        fun parse(data: String): LogChangeNeedleSuccess {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogChangeNeedleSuccess(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}