package app.aaps.pump.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * System Reset Log
 */
@Suppress("SpellCheckingInspection")
class LogResetSysV3 private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    val batteryRemain: Byte,
    val reason: Byte,  // 사유(1:공장초기화 후 리셋, 2:긴급정지 해제 후 리셋, 3:사용자 배터리 교체 후 리셋, 4:캘리브레이션 후 리셋, 9:예상치 못한 시스템 리셋)
    private val rcon1: Short,  // PIC 데이터 시트 내 정의된 2바이트 값
    private val rcon2: Short   // PIC 데이터 시트 내 정의된 2바이트 값
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_RESET_SYS_V3{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append(", reason=").append(reason.toInt())
        sb.append(", rcon1=").append(rcon1.toInt())
        sb.append(", rcon2=").append(rcon2.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x01
        fun parse(data: String): LogResetSysV3 {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogResetSysV3(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getShort(buffer)
            )
        }
    }

}