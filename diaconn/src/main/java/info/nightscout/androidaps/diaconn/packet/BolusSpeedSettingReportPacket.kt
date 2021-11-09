package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

/**
 * BolusSpeedSettingReportPacket
 */
class BolusSpeedSettingReportPacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var sp: SP
    init {
        msgType = 0xC5.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingReportPacket init ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedSettingReportPacket Report Packet Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        diaconnG8Pump.speed   = getByteToInt(bufferData) // speed result
        sp.putBoolean("diaconn_g8_isbolusspeedsync", true)
        aapsLogger.debug(LTag.PUMPCOMM, "bolusSpeed   --> ${diaconnG8Pump.speed  }")
    }

    override fun getFriendlyName(): String {
        return "PUMP_BOLUS_SPEED_SETTING_REPORT"
    }
}