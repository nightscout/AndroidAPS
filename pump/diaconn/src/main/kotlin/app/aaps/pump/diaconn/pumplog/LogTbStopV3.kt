package app.aaps.pump.diaconn.pumplog

import app.aaps.pump.common.utils.and
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
* 임시기저 중지(완료)
*/
@Suppress("SpellCheckingInspection")
class LogTbStopV3 private constructor(
    val data: String,
    val dttm: String,
    typeAndKind: Byte,
    // 임시기저 주입량/률, Rate: 1000(0.00U) ~ 1600(6.00U), Ratio: 50000(0%) ~ 50200(200%), 50000이상이면 주입률로 판정, 별도 계산/역산식 참조
    private val tbInjectRateRatio: Short,
    // 0=완료, 4=사용자중단, 6=기타, 7=긴급정지
    val reason: Byte,
    // 앱에서 생성 전달한 임시기저 시작(요청) 시간
    private val tbDttm: String
) {

    val type: Byte = PumpLogUtil.getType(typeAndKind)
    val kind: Byte = PumpLogUtil.getKind(typeAndKind)

    fun getTbInjectRateRatio(): Int {
        return tbInjectRateRatio and 0xffff
    }

    override fun toString(): String {
        val sb = StringBuilder("LOG_TB_STOP_V3{")
        sb.append("LOG_KIND=").append(LOG_KIND.toInt())
        sb.append(", data='").append(data).append('\'')
        sb.append(", dttm='").append(dttm).append('\'')
        sb.append(", type=").append(type.toInt())
        sb.append(", kind=").append(kind.toInt())
        sb.append(", tbInjectRateRatio=").append(tbInjectRateRatio and 0xffff)
        sb.append(", reason=").append(reason.toInt())
        if (tbDttm != PumpLogUtil.getDttm("ffffffff")) sb.append(", tbDttm=").append(tbDttm)
        sb.append('}')
        return sb.toString()
    }

    companion object {

        const val LOG_KIND: Byte = 0x13
        fun parse(data: String): LogTbStopV3 {
            val bytes = PumpLogUtil.hexStringToByteArray(data)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return LogTbStopV3(
                data,
                PumpLogUtil.getDttm(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getShort(buffer),
                PumpLogUtil.getByte(buffer),
                PumpLogUtil.getDttm(buffer)
            )
        }
    }

}