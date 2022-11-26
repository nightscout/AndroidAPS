package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * TimeInquireResponsePacket
 */
class TimeInquireResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x8F.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "TimeInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "TimeInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        if(!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }
        diaconnG8Pump.year = getByteToInt(bufferData) + 2000   // 년 (18~99)
        diaconnG8Pump.month = getByteToInt(bufferData)  // 월 (1~12)
        diaconnG8Pump.day = getByteToInt(bufferData)    // 일 (1~31)
        diaconnG8Pump.hour = getByteToInt(bufferData)   // 시 (0~23)
        diaconnG8Pump.minute = getByteToInt(bufferData) // 분 (0~59)
        diaconnG8Pump.second = getByteToInt(bufferData) // 초 (0~59)
    }

    override fun getFriendlyName(): String {
        return "PUMP_TIME_INQUIRE_RESPONSE"
    }
}