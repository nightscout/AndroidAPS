package app.aaps.pump.diaconn.pumplog

import app.aaps.pump.common.utils.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 듀얼주입 설정(시작)
*/
@Suppress("SpellCheckingInspection")
class LogSetDualInjection private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    private val setNormAmount: Short, // 47.5=4750
    val setSquareAmount: Short, // 47.5=4750
    private val injectTime: Byte, // 1~30( 1: 10min )
    val batteryRemain: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)
    fun getInjectTime(): Int {
        return injectTime and 0xff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_SET_DUAL_INJECTION{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", setNormAmount=").append(setNormAmount.toInt())
        sb.append(", setSquareAmount=").append(setSquareAmount.toInt())
        sb.append(", injectTime=").append(injectTime and 0xff)
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x0F
        fun parse(data: String): LogSetDualInjection {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogSetDualInjection(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}