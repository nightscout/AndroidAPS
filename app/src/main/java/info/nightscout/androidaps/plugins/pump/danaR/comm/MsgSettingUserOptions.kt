package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import java.util.*

class MsgSettingUserOptions(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : MessageBase() {


    init {
        SetCommand(0x320B)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(packet: ByteArray) {
        val bytes = getDataBytes(packet, packet.size - 10)
        danaRPump.userOptionsFrompump = Arrays.copyOf(bytes, bytes!!.size) // saving pumpDataBytes to use it in MsgSetUserOptions
        for (pos in bytes.indices) {
            aapsLogger.debug(LTag.PUMPCOMM, "[" + pos + "]" + bytes[pos])
        }
        danaRPump.timeDisplayType = if (bytes[0] == 1.toByte()) 0 else 1 // 1 -> 24h 0 -> 12h
        danaRPump.buttonScrollOnOff = if (bytes[1] == 1.toByte()) 1 else 0 // 1 -> ON, 0-> OFF
        danaRPump.beepAndAlarm = bytes[2].toInt() // 1 -> Sound on alarm 2-> Vibrate on alarm 3-> Both on alarm 5-> Sound + beep 6-> vibrate + beep 7-> both + beep Beep adds 4
        danaRPump.lcdOnTimeSec = bytes[3].toInt() and 255
        danaRPump.backlightOnTimeSec = bytes[4].toInt() and 255
        danaRPump.selectedLanguage = bytes[5].toInt() // on DanaRv2 is that needed ?
        danaRPump.units = bytes[8].toInt()
        danaRPump.shutdownHour = bytes[9].toInt()
        danaRPump.lowReservoirRate = bytes[32].toInt() and 255
        /* int selectableLanguage1 = bytes[10];
        int selectableLanguage2 = bytes[11];
        int selectableLanguage3 = bytes[12];
        int selectableLanguage4 = bytes[13];
        int selectableLanguage5 = bytes[14];
        */
        aapsLogger.debug(LTag.PUMPCOMM, "timeDisplayType: " + danaRPump.timeDisplayType)
        aapsLogger.debug(LTag.PUMPCOMM, "Button scroll: " + danaRPump.buttonScrollOnOff)
        aapsLogger.debug(LTag.PUMPCOMM, "BeepAndAlarm: " + danaRPump.beepAndAlarm)
        aapsLogger.debug(LTag.PUMPCOMM, "screen timeout: " + danaRPump.lcdOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "BackLight: " + danaRPump.backlightOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "Selected language: " + danaRPump.selectedLanguage)
        aapsLogger.debug(LTag.PUMPCOMM, "Units: " + danaRPump.getUnits())
        aapsLogger.debug(LTag.PUMPCOMM, "Shutdown: " + danaRPump.shutdownHour)
        aapsLogger.debug(LTag.PUMPCOMM, "Low reservoir: " + danaRPump.lowReservoirRate)
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