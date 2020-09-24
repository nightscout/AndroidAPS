package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import java.util.*

class MsgSettingUserOptions(
    injector: HasAndroidInjector
) : MessageBase(injector) {


    init {
        SetCommand(0x320B)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(packet: ByteArray) {
        val bytes = getDataBytes(packet, packet.size - 10)
        danaPump.userOptionsFrompump = Arrays.copyOf(bytes, bytes!!.size) // saving pumpDataBytes to use it in MsgSetUserOptions
        for (pos in bytes.indices) {
            aapsLogger.debug(LTag.PUMPCOMM, "[" + pos + "]" + bytes[pos])
        }
        danaPump.timeDisplayType24 = bytes[0].toInt() == 0 // 0 -> 24h 1 -> 12h
        danaPump.buttonScrollOnOff = bytes[1] == 1.toByte() // 1 -> ON, 0-> OFF
        danaPump.beepAndAlarm = bytes[2].toInt() // 1 -> Sound on alarm 2-> Vibrate on alarm 3-> Both on alarm 5-> Sound + beep 6-> vibrate + beep 7-> both + beep Beep adds 4
        danaPump.lcdOnTimeSec = bytes[3].toInt()
        danaPump.backlightOnTimeSec = bytes[4].toInt()
        danaPump.selectedLanguage = bytes[5].toInt() // on DanaRv2 is that needed ?
        danaPump.units = bytes[8].toInt()
        danaPump.shutdownHour = bytes[9].toInt()
        danaPump.lowReservoirRate = bytes[32].toInt()
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

    private fun getDataBytes(bytes: ByteArray?, len: Int): ByteArray? {
        if (bytes == null) {
            return null
        }
        val ret = ByteArray(len)
        System.arraycopy(bytes, 6, ret, 0, len)
        return ret
    }
}