package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import app.aaps.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * ConfirmReportPacket
 */
class ConfirmReportPacket(
    injector: HasAndroidInjector
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
        aapsLogger.debug(LTag.PUMPCOMM, "Pump Report Confirm reqMsgType --> $reqMsgType")
    }

    override val friendlyName = "PUMP_CONFIRM_REPORT"
}