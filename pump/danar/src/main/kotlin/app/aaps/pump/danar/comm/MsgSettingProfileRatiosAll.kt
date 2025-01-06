package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector

class MsgSettingProfileRatiosAll(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x320D)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaPump.units == DanaPump.UNITS_MGDL) {
            danaPump.morningCIR = intFromBuff(bytes, 0, 2)
            danaPump.morningCF = intFromBuff(bytes, 2, 2).toDouble()
            danaPump.afternoonCIR = intFromBuff(bytes, 4, 2)
            danaPump.afternoonCF = intFromBuff(bytes, 6, 2).toDouble()
            danaPump.eveningCIR = intFromBuff(bytes, 8, 2)
            danaPump.eveningCF = intFromBuff(bytes, 10, 2).toDouble()
            danaPump.nightCIR = intFromBuff(bytes, 12, 2)
            danaPump.nightCF = intFromBuff(bytes, 14, 2).toDouble()
        } else {
            danaPump.morningCIR = intFromBuff(bytes, 0, 2)
            danaPump.morningCF = intFromBuff(bytes, 2, 2) / 100.0
            danaPump.afternoonCIR = intFromBuff(bytes, 4, 2)
            danaPump.afternoonCF = intFromBuff(bytes, 6, 2) / 100.0
            danaPump.eveningCIR = intFromBuff(bytes, 8, 2)
            danaPump.eveningCF = intFromBuff(bytes, 10, 2) / 100.0
            danaPump.nightCIR = intFromBuff(bytes, 12, 2)
            danaPump.nightCF = intFromBuff(bytes, 14, 2) / 100.0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CIR: " + danaPump.morningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CF: " + danaPump.morningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CIR: " + danaPump.afternoonCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CF: " + danaPump.afternoonCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CIR: " + danaPump.eveningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CF: " + danaPump.eveningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CIR: " + danaPump.nightCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CF: " + danaPump.nightCF)
    }
}