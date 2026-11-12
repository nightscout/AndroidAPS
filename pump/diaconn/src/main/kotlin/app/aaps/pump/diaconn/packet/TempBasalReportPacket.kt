package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * TempBasalReportPacket
 */
class TempBasalReportPacket(
    injector: MetroMemberInjector
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

        // The status, the time in 15-minute steps and the rate, as in the temp basal inquiry. Real G8 fw 2.50
        // (2026-11-11), 1.8 s after the confirm of a temp basal of 1.15 U/h for 30 min: 01 02 5b 04 (running, 2, 1115).
        // It took only the status and kept the time and rate of the last inquiry, so the pump object said "running"
        // with the rate of the temp basal before (50100 when none ran).
        val bufferData = prefixDecode(data)
        diaconnG8Pump.tbStatus = getByteToInt(bufferData)
        diaconnG8Pump.tbTime = getByteToInt(bufferData)
        diaconnG8Pump.tbInjectRateRatio = getShortToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "tbStatus > " + diaconnG8Pump.tbStatus)
        aapsLogger.debug(LTag.PUMPCOMM, "tbTime> " + diaconnG8Pump.tbTime)
        aapsLogger.debug(LTag.PUMPCOMM, "tbInjectRateRatio > " + diaconnG8Pump.tbInjectRateRatio)

    }

    override val friendlyName = "PUMP_TEMP_BASAL_REPORT"
}