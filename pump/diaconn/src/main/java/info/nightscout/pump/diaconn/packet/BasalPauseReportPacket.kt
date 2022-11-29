package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

/**
 * BasalPauseReportPacket
 */
class BasalPauseReportPacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var status:Int? = null

    init {
        msgType = 0xC3.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BasalPauseReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BasalPauseReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        status =  getByteToInt(bufferData) //(1: pauseed, 2: pause cancel)
        aapsLogger.debug(LTag.PUMPCOMM, "status --> ${status}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_BASAL_PAUSE_REPORT"
    }
}