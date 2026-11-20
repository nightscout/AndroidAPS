package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * SoundInquireResponsePacket
 */
class SoundInquireResponsePacket(injector: MetroMemberInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump

    init {
        msgType = 0x8D.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "SoundInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
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

        // As the pump sends them, 1..3, as the setting takes them. They were made 0..2 here: a real G8 set to silent and
        // low (2026-11-12, `03 01`) showed as vibrate and no intensity, and a save of the alarm sent intensity 0.
        diaconnG8Pump.beepAndAlarm = getByteToInt(bufferData)
        diaconnG8Pump.alarmIntensity = getByteToInt(bufferData)

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "beepAndAlarm --> ${diaconnG8Pump.beepAndAlarm}")
        aapsLogger.debug(LTag.PUMPCOMM, "alarmIntensity --> ${diaconnG8Pump.alarmIntensity}")
    }

    override val friendlyName = "PUMP_SOUND_INQUIRE_RESPONSE"
}
