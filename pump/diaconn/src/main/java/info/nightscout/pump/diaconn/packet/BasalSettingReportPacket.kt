package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * BasalSettingReportPacket
 */
class BasalSettingReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result = 0

    init {
        msgType = 0xCB.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BasalSettingReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
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

    override fun getFriendlyName(): String {
        return "PUMP_BASAL_SETTING_REPORT"
    }
}