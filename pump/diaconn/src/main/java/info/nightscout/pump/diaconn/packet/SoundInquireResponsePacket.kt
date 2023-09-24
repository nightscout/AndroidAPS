package info.nightscout.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.pump.diaconn.DiaconnG8Pump
import javax.inject.Inject

/**
 * SoundInquireResponsePacket
 */
class SoundInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x8D.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "SoundInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray?) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "SoundInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }

        diaconnG8Pump.beepAndAlarm = getByteToInt(bufferData) - 1
        diaconnG8Pump.alarmIntensity = getByteToInt(bufferData) - 1

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "beepAndAlarm --> ${diaconnG8Pump.beepAndAlarm}")
        aapsLogger.debug(LTag.PUMPCOMM, "alarmIntensity --> ${diaconnG8Pump.alarmIntensity}")
    }

    override fun getFriendlyName(): String {
        return "PUMP_SOUND_INQUIRE_RESPONSE"
    }
}
