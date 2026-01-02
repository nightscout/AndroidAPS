package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * TempBasalReportPacket
 */
class TempBasalReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xCA.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasalReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "TempBasalReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.tbStatus = getByteToInt(bufferData)
        //diaconnG8Pump.isTempBasalInProgress = diaconnG8Pump.tbStatus == 1

        // 응답받은 임시기저 상태가 주입중이면, pump객체에 정보를 갱신.
        // if(diaconnG8Pump.isTempBasalInProgress) {
        //     diaconnG8Pump.tbTime =  getByteToInt(bufferData)
        //     diaconnG8Pump.tbInjectRateRatio =  getShortToInt(bufferData)
        //     if (diaconnG8Pump.tbInjectRateRatio >= 50000) {
        //         diaconnG8Pump.tempBasalPercent = diaconnG8Pump.tbInjectRateRatio - 50000
        //     }
        //
        //     if(diaconnG8Pump.tbInjectRateRatio in 1000..1600) {
        //         diaconnG8Pump.tbInjectAbsoluteValue = (diaconnG8Pump.tbInjectRateRatio -1000) / 100.0
        //         diaconnG8Pump.tempBasalAbsoluteRate = diaconnG8Pump.tbInjectAbsoluteValue
        //     }
        // }
        aapsLogger.debug(LTag.PUMPCOMM, "tbStatus > " + diaconnG8Pump.tbStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "tbTime> " + diaconnG8Pump.tbTime)
        aapsLogger.debug(LTag.PUMPCOMM, "tbInjectRateRatio > " + diaconnG8Pump.tbInjectRateRatio)

    }

    override val friendlyName = "PUMP_TEMP_BASAL_REPORT"
}