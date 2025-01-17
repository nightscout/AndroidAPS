package app.aaps.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* Injection Blocked Alarm Log
*/
class LogAlarmBlock private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte, // 1=INFO, 2=WARNING, 3=MAJOR, 4=CRITICAL
    private val alarmLevel: Byte,     // 1=OCCUR
    private val ack: Byte,
    val amount: Short, // 1=BASE, 2=Meal, 3=snack , 4=square, 5=dual, 6=tube change, 7=needle change, 8=insulin change
    val reason: Byte,
    val batteryRemain: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_ALARM_BLOCK{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", alarmLevel=").append(alarmLevel.toInt())
        sb.append(", ack=").append(ack.toInt())
        sb.append(", amount=").append(amount.toInt())
        sb.append(", reason=").append(reason.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x29
        fun parse(data: String): LogAlarmBlock {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogAlarmBlock(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}