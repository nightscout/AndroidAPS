package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import dev.zacsweers.metro.Inject

/**
 * BasalPauseReportPacket
 */
class BasalPauseReportPacket(injector: MetroMemberInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var status: Int? = null

    init {
        msgType = 0xC3.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BasalPauseReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BasalPauseReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        status = getByteToInt(bufferData) //(1: paused, 2: pause cancel)
        aapsLogger.debug(LTag.PUMPCOMM, "status --> $status")
    }

    override val friendlyName = "PUMP_BASAL_PAUSE_REPORT"
}