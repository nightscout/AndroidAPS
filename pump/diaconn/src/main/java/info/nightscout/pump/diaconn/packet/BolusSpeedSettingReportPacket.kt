package info.nightscout.pump.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import info.nightscout.pump.diaconn.R
import info.nightscout.rx.logging.LTag

import info.nightscout.shared.sharedPreferences.SP
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
        sp.putBoolean(R.string.key_diaconn_g8_is_bolus_speed_sync, true)
        aapsLogger.debug(LTag.PUMPCOMM, "bolusSpeed   --> ${diaconnG8Pump.speed}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_BOLUS_SPEED_SETTING_REPORT"
    }
}