package info.nightscout.androidaps.diaconn.pumplog

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 당일 주입 총량 (식사, 추가)
*/
class LOG_INJECTION_1DAY private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,    // 당일 식사주입 총량 47.5=4750
    val mealAmount: Short,    // 당일 추가주입 총량 47.5=4750
    val extAmount: Short,
    val batteryRemain: Byte
) {

    val type: Byte = PumplogUtil.getType(typeAndKind)
    val kind: Byte = PumplogUtil.getKind(typeAndKind)

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECTION_1DAY{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", mealAmount=").append(mealAmount.toInt())
        sb.append(", extAmount=").append(extAmount.toInt())
        sb.append(", batteryRemain=").append(batteryRemain.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x2F
        fun parse(data: String): LOG_INJECTION_1DAY {
            val bytes = PumplogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LOG_INJECTION_1DAY(
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