package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.LTag


class MsgSettingUserOptions(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x320B)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val data = getDataBytes(bytes, bytes.size - 10)
        danaPump.userOptionsFromPump = data.copyOf(data.size) // saving pumpDataBytes to use it in MsgSetUserOptions
        for (pos in data.indices) {
            aapsLogger.debug(LTag.PUMPCOMM, "[" + pos + "]" + data[pos])
        }
        danaPump.timeDisplayType24 = data[0].toInt() == 0 // 0 -> 24h 1 -> 12h
        danaPump.buttonScrollOnOff = data[1] == 1.toByte() // 1 -> ON, 0-> OFF
        danaPump.beepAndAlarm = data[2].toInt() // 1 -> Sound on alarm 2-> Vibrate on alarm 3-> Both on alarm 5-> Sound + beep 6-> vibrate + beep 7-> both + beep Beep adds 4
        danaPump.lcdOnTimeSec = data[3].toInt()
        danaPump.backlightOnTimeSec = data[4].toInt()
        danaPump.selectedLanguage = data[5].toInt() // on DanaRv2 is that needed ?
        danaPump.units = data[8].toInt()
        danaPump.shutdownHour = data[9].toInt()
        danaPump.lowReservoirRate = data[32].toInt()
        /* int selectableLanguage1 = bytes[10];
        int selectableLanguage2 = bytes[11];
        int selectableLanguage3 = bytes[12];
        int selectableLanguage4 = bytes[13];
        int selectableLanguage5 = bytes[14];
        */
        aapsLogger.debug(LTag.PUMPCOMM, "timeDisplayType24: " + danaPump.timeDisplayType24)
        aapsLogger.debug(LTag.PUMPCOMM, "Button scroll: " + danaPump.buttonScrollOnOff)
        aapsLogger.debug(LTag.PUMPCOMM, "BeepAndAlarm: " + danaPump.beepAndAlarm)
        aapsLogger.debug(LTag.PUMPCOMM, "screen timeout: " + danaPump.lcdOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "BackLight: " + danaPump.backlightOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "Selected language: " + danaPump.selectedLanguage)
        aapsLogger.debug(LTag.PUMPCOMM, "Units: " + danaPump.getUnits())
        aapsLogger.debug(LTag.PUMPCOMM, "Shutdown: " + danaPump.shutdownHour)
        aapsLogger.debug(LTag.PUMPCOMM, "Low reservoir: " + danaPump.lowReservoirRate)
    }

    private fun getDataBytes(bytes: ByteArray, len: Int): ByteArray {
        val ret = ByteArray(len)
        System.arraycopy(bytes, 6, ret, 0, len)
        return ret
    }
}
