package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump

class MsgSetUserOptions(
    injector: MetroMemberInjector
) : MessageBase(injector) {

    /** The user options this write sends, for the service to keep once the pump took them. Null: nothing to write. */
    var written: DanaPump.UserOptions? = null
        private set

    init {
        setCommand(0x330B)
        if (danaPump.userOptionsFromPump == null) {
            // No options set -> Exiting
            aapsLogger.debug(LTag.PUMPCOMM, "NO USER OPTIONS LOADED EXITING!")
        } else {
            val options = danaPump.userOptionsForWrite().also { written = it }
            danaPump.userOptionsFromPump!![0] = if (options.timeDisplayType24) 0.toByte() else 1.toByte()
            danaPump.userOptionsFromPump!![1] = if (options.buttonScrollOnOff) 1.toByte() else 0.toByte()
            danaPump.userOptionsFromPump!![2] = options.beepAndAlarm.toByte()
            danaPump.userOptionsFromPump!![3] = options.lcdOnTimeSec.toByte()
            danaPump.userOptionsFromPump!![4] = options.backlightOnTimeSec.toByte()
            danaPump.userOptionsFromPump!![5] = danaPump.selectedLanguage.toByte()
            danaPump.userOptionsFromPump!![8] = options.units.toByte()
            danaPump.userOptionsFromPump!![9] = options.shutdownHour.toByte()
            // Not byte 32, where MsgSettingUserOptions reads it: a real DanaRv2 takes it from byte 27 (2026-11-11).
            danaPump.userOptionsFromPump!![27] = options.lowReservoirRate.toByte()
            for (element in danaPump.userOptionsFromPump!!) {
                addParamByte(element)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "New message")
        }
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result ERROR!!!")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result")
        }
    }
}