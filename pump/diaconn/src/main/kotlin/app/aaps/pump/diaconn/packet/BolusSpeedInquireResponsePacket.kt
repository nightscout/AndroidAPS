package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnIntKey
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * BolusSpeedInquireResponsePacket
 */
@Suppress("SpellCheckingInspection")
class BolusSpeedInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var preferences: Preferences

    init {
        msgType = 0x85.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "BolusSpeedInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
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
        preferences.put(DiaconnIntKey.BolusSpeed, diaconnG8Pump.speed)

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusSpeed > " + diaconnG8Pump.speed)
    }

    override val friendlyName = "PUMP_BOLUS_SPEED_INQUIRE_RESPONSE"
}