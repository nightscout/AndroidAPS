package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * BolusSpeedSettingReportPacket
 */
class BolusSpeedSettingReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0xC5.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingReportPacket Report Packet Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.speed = getByteToInt(bufferData) // speed result
        aapsLogger.debug(LTag.PUMPCOMM, "bolusSpeed   --> ${diaconnG8Pump.speed}")
    }

    override val friendlyName = "PUMP_BOLUS_SPEED_SETTING_REPORT"
}