package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector

class MsgSettingProfileRatios(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x3204)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaPump.units == DanaPump.UNITS_MGDL) {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2).toDouble()
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2).toDouble()
            danaPump.currentAIDR = intFromBuff(bytes, 8, 1)
        } else {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2) / 100.0
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2) / 100.0
            danaPump.currentAIDR = intFromBuff(bytes, 8, 1)
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CIR: " + danaPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CF: " + danaPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AI: " + danaPump.currentAI)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump target: " + danaPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AIDR: " + danaPump.currentAIDR)
    }
}