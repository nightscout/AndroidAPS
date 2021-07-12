package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

/**
 * RejectReportPacket
 */
class RejectReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var reqMsgType:Int? = null
    var reason:Int? = null
    init {
        msgType = 0xE2.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "RejectReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "RejectReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        reqMsgType =  getByteToInt(bufferData)
        reason =  getByteToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "reqMsgType --> $reqMsgType")
        aapsLogger.debug(LTag.PUMPCOMM, "Reject Reason --> $reason (6:cancel, 10:timeout) ")
    }

    override fun getFriendlyName(): String {
        return "PUMP_REJECT_REPORT"
    }
}