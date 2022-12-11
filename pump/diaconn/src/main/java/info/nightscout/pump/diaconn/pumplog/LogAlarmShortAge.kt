package info.nightscout.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* Insulin shortage alarm
*/
class LogAlarmShortAge private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte, // 1=INFO, 2=WARNING, 3=MAJOR, 4=CRITICAL
    private val alarmLevel: Byte, // 1=OCCUR, 2=STOP
    private val ack: Byte,     // (1~100U)
    val remain: Byte,
    val batteryRemain: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_ALARM_SHORTAGE{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", alarmLevel=").append(alarmLevel.toInt())
        sb.append(", ack=").append(ack.toInt())
        sb.append(", remain=").append(remain.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x2A
        fun parse(data: String): LogAlarmShortAge {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogAlarmShortAge(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}