package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector

class MsgStatusProfile(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0204)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (danaPump.units == DanaPump.UNITS_MGDL) {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2).toDouble()
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2).toDouble()
        } else {
            danaPump.currentCIR = intFromBuff(bytes, 0, 2)
            danaPump.currentCF = intFromBuff(bytes, 2, 2) / 100.0
            danaPump.currentAI = intFromBuff(bytes, 4, 2) / 100.0
            danaPump.currentTarget = intFromBuff(bytes, 6, 2) / 100.0
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CIR: " + danaPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump CF: " + danaPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AI: " + danaPump.currentAI)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump target: " + danaPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump AIDR: " + danaPump.currentAIDR)
    }
}