package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Option_Set_User_Option(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {


    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Setting user settings")
    }

    override fun getRequestParams(): ByteArray {
        aapsLogger.debug(LTag.PUMPCOMM,
            "UserOptions:" + (System.currentTimeMillis() - danaRPump.lastConnection) / 1000 + " s ago"
                + "\ntimeDisplayType:" + danaRPump.timeDisplayType
                + "\nbuttonScroll:" + danaRPump.buttonScrollOnOff
                + "\ntimeDisplayType:" + danaRPump.timeDisplayType
                + "\nlcdOnTimeSec:" + danaRPump.lcdOnTimeSec
                + "\nbacklight:" + danaRPump.backlightOnTimeSec
                + "\ndanaRPumpUnits:" + danaRPump.units
                + "\nlowReservoir:" + danaRPump.lowReservoirRate)
        val request = ByteArray(13)
        request[0] = (danaRPump.timeDisplayType and 0xff).toByte()
        request[1] = (danaRPump.buttonScrollOnOff and 0xff).toByte()
        request[2] = (danaRPump.beepAndAlarm and 0xff).toByte()
        request[3] = (danaRPump.lcdOnTimeSec and 0xff).toByte()
        request[4] = (danaRPump.backlightOnTimeSec and 0xff).toByte()
        request[5] = (danaRPump.selectedLanguage and 0xff).toByte()
        request[6] = (danaRPump.units and 0xff).toByte()
        request[7] = (danaRPump.shutdownHour and 0xff).toByte()
        request[8] = (danaRPump.lowReservoirRate and 0xff).toByte()
        request[9] = (danaRPump.cannulaVolume and 0xff).toByte()
        request[10] = (danaRPump.cannulaVolume ushr 8 and 0xff).toByte()
        request[11] = (danaRPump.refillAmount and 0xff).toByte()
        request[12] = (danaRPump.refillAmount ushr 8 and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "OPTION__SET_USER_OPTION"
    }
}