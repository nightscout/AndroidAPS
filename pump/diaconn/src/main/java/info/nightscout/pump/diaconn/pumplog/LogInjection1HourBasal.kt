package info.nightscout.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 1시간 단위 기저 주입량
*/
@Suppress("SpellCheckingInspection")
class LogInjection1HourBasal private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    tbBeforeAmount: Short,
    tbAfterAmount: Short,
    val batteryRemain: Byte,
    // 남은 전체 인슐린 량(47.5=4750)
    private val remainTotalAmount: Short
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)
    val beforeAmount // 해당시간의 임시기저 계산 전 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)
        : Short = tbBeforeAmount
    val afterAmount // 해당시간의 임시기저 계산 후 기저주입량: 기저주입막힘 발생 시 기저주입 막힘량 제외, 기저정지로 인해 주입되지 않은 량 제외, 리셋으로 인해 주입되지 않은 량 제외(47.5=4750)
        : Short = tbAfterAmount

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECTION_1HOUR_BASAL{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", tbBeforeAmount=").append(beforeAmount.toInt())
        sb.append(", tbAfterAmount=").append(afterAmount.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append(", remainTotalAmount=").append(remainTotalAmount.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x2C
        fun parse(data: String): LogInjection1HourBasal {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogInjection1HourBasal(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer)
            )
        }
    }

}