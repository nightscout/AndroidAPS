package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.core.interfaces.di.MetroMemberInjector
import dev.zacsweers.metro.Inject

/**
 * BasalSettingReportPacket
 */
class BasalSettingReportPacket(
    injector: MetroMemberInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result = 0

    init {
        msgType = 0xCB.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BasalSettingReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BasalSettingReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        result = getByteToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        // no Response
    }

    override val friendlyName = "PUMP_BASAL_SETTING_REPORT"
}