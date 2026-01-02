package app.aaps.pump.diaconn.pumplog

import app.aaps.pump.common.utils.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 식사주입 실패
*/
@Suppress("SpellCheckingInspection")
class LogInjectMealFail private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,     // 47.5=4750
    private val setAmount: Short,     // 47.5=4750
    val injectAmount: Short,     // 1분단위 주입시간(124=124분=2시간4분)
    private val injectTime: Byte,     // 아침=1, 점심=2, 저녁=3
    val time: Byte,     // 1=주입막힘, 2=배터리잔량부족, 3=약물부족, 4=사용자중지, 5=시스템리셋, 6=기타, 7=긴급정지
    val reason: Byte
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    fun getInjectTime(): Int {
        return injectTime and 0xff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_INJECT_MEAL_FAIL{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", setAmount=").append(setAmount.toInt())
        sb.append(", injectAmount=").append(injectAmount.toInt())
        sb.append(", injectTime=").append(injectTime and 0xff)
        sb.append(", time=").append(time.toInt())
        sb.append(", reason=").append(reason.toInt())
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x09
        fun parse(data: String): LogInjectMealFail {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogInjectMealFail(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getByte(buffer)
            )
        }
    }

}