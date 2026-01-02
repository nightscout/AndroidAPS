package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * LogStatusInquireResponsePacket
 */
open class LogStatusInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x96.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "LogStatusInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "LogStatusInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }
        // 5. 로그상태 조회
        diaconnG8Pump.pumpLastLogNum = getShortToInt(bufferData) // 마지막 저장로그번호
        diaconnG8Pump.pumpWrappingCount = getByteToInt(bufferData) // wrapping 카운트

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "pumpLastLogNum > " + diaconnG8Pump.pumpLastLogNum)
        aapsLogger.debug(LTag.PUMPCOMM, "pumpWrappingCount> " + diaconnG8Pump.pumpWrappingCount)
    }

    override val friendlyName = "PUMP_LOG_STATUS_INQUIRE_RESPONSE"
}