package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * BolusSpeedInquireResponsePacket
 */
class BolusSpeedInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var sp: SP

    init {
        msgType = 0x85.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val defectResult = defect(data)
        if (defectResult != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)

        if (!isSuccInquireResponseResult(result)) {
            failed = true
            return
        }

        diaconnG8Pump.speed = getByteToInt(bufferData) //주입속도
        sp.putString("g8_bolusspeed", diaconnG8Pump.speed.toString())

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusSpeed > " + diaconnG8Pump.speed)
    }

    override fun getFriendlyName(): String {
        return "PUMP_BOLUS_SPEED_INQUIRE_RESPONSE"
    }

}