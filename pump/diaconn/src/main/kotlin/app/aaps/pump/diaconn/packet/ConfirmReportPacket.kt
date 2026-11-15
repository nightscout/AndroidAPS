package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * The pump has carried out a setting it confirmed. Real G8 fw 2.50 (2026-11-11): 1.2 to 1.9 s after the answer to the
 * confirm of a temp basal or an extended bolus, mostly with 0x37 (once 0x16).
 */
class ConfirmReportPacket(
    injector: MetroMemberInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var reqMsgType: Int? = null

    init {
        msgType = 0xE8.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "ConfirmReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "ConfirmReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        reqMsgType = getByteToInt(bufferData)
        diaconnG8Pump.confirmReports++
        aapsLogger.debug(LTag.PUMPCOMM, "Pump Report Confirm reqMsgType --> $reqMsgType")
    }

    override val friendlyName = "PUMP_CONFIRM_REPORT"
}