package info.nightscout.androidaps.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgSetUserOptions(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x330B)
        if (danaPump.userOptionsFromPump == null) {
            // No options set -> Exiting
            aapsLogger.debug(LTag.PUMPCOMM, "NO USER OPTIONS LOADED EXITING!")
        } else {
            danaPump.userOptionsFromPump!![0] = if (danaPump.timeDisplayType24) 0.toByte() else 1.toByte()
            danaPump.userOptionsFromPump!![1] = if (danaPump.buttonScrollOnOff) 1.toByte() else 0.toByte()
            danaPump.userOptionsFromPump!![2] = danaPump.beepAndAlarm.toByte()
            danaPump.userOptionsFromPump!![3] = danaPump.lcdOnTimeSec.toByte()
            danaPump.userOptionsFromPump!![4] = danaPump.backlightOnTimeSec.toByte()
            danaPump.userOptionsFromPump!![5] = danaPump.selectedLanguage.toByte()
            danaPump.userOptionsFromPump!![8] = danaPump.units.toByte()
            danaPump.userOptionsFromPump!![9] = danaPump.shutdownHour.toByte()
            danaPump.userOptionsFromPump!![27] = danaPump.lowReservoirRate.toByte()
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
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result FAILED!!!")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Setting user options: $result")
        }
    }
}