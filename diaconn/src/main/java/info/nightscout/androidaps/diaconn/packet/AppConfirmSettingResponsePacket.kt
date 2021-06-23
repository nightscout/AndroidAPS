package info.nightscout.androidaps.diaconn.packet

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.diaconn.DiaconnG8Pump
import info.nightscout.androidaps.logging.LTag
import javax.inject.Inject

/**
 * AppConfirmSettingResponsePacket
 */
class AppConfirmSettingResponsePacket(
    injector: HasAndroidInjector
) : DiaconnG8Packet(injector ) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    var result =0
    init {
        msgType = 0xB7.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "AppConfirmSettingReqPacket Response ")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "AppConfirmSettingResponsePacket Got some Error")
            failed = true
            return
        } else failed = false
        val bufferData = prefixDecode(data)
        result =  getByteToInt(bufferData)
        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${result}")

        if(!isSuccSettingResponseResult(result)) {
            diaconnG8Pump.resultErrorCode = result
            failed = true
            return
        }
    }

    override fun getFriendlyName(): String {
        return "PUMP_APP_CONFIRM_SETTING_RESPONSE"
    }
}