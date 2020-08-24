package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag

class MsgSetUserOptions(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        SetCommand(0x330B)
        if (danaPump.userOptionsFrompump == null) {
            // No options set -> Exiting
            aapsLogger.debug(LTag.PUMPCOMM, "NO USER OPTIONS LOADED EXITING!")
        } else {
            danaPump.userOptionsFrompump!![0] = if( danaPump.timeDisplayType24) 0.toByte() else 1.toByte()
            danaPump.userOptionsFrompump!![1] = if (danaPump.buttonScrollOnOff) 1.toByte() else 0.toByte()
            danaPump.userOptionsFrompump!![2] = danaPump.beepAndAlarm.toByte()
            danaPump.userOptionsFrompump!![3] = danaPump.lcdOnTimeSec.toByte()
            danaPump.userOptionsFrompump!![4] = danaPump.backlightOnTimeSec.toByte()
            danaPump.userOptionsFrompump!![5] = danaPump.selectedLanguage.toByte()
            danaPump.userOptionsFrompump!![8] = danaPump.units.toByte()
            danaPump.userOptionsFrompump!![9] = danaPump.shutdownHour.toByte()
            danaPump.userOptionsFrompump!![27] = danaPump.lowReservoirRate.toByte()
            for (element in danaPump.userOptionsFrompump!!) {
                AddParamByte(element)
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