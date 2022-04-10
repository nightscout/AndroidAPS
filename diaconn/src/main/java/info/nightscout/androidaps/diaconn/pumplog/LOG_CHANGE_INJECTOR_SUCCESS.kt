package info.nightscout.androidaps.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 주사기 교체 성공
*/
class LOG_CHANGE_INJECTOR_SUCCESS private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    // 47.5=4750
    val primeAmount: Short,    // 47.5=4750
    val remainAmount: Short,
    val batteryRemain: Byte
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_CHANGE_INJECTOR_SUCCESS{")
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

        const val LOG_KIND: Byte = 0x1A
        fun parse(data: String): LOG_CHANGE_INJECTOR_SUCCESS {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_CHANGE_INJECTOR_SUCCESS(
                data,
                PumplogUtil.getDttm(buffer),
                PumplogUtil.getByte(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getShort(buffer),
                PumplogUtil.getByte(buffer)
            )
        }
    }

}