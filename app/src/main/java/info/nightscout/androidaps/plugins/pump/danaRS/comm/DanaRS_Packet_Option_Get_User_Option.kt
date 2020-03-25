package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Option_Get_User_Option(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting user settings")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        danaRPump.timeDisplayType = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.buttonScrollOnOff = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.beepAndAlarm = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.lcdOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.backlightOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.selectedLanguage = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.shutdownHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.lowReservoirRate = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.cannulaVolume = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.refillAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage1 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage2 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage3 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage4 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage5 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        // Pump's screen on time can't be less than 5
        failed = if (danaRPump.lcdOnTimeSec < 5) true else false
        aapsLogger.debug(LTag.PUMPCOMM, "timeDisplayType: " + danaRPump.timeDisplayType)
        aapsLogger.debug(LTag.PUMPCOMM, "buttonScrollOnOff: " + danaRPump.buttonScrollOnOff)
        aapsLogger.debug(LTag.PUMPCOMM, "beepAndAlarm: " + danaRPump.beepAndAlarm)
        aapsLogger.debug(LTag.PUMPCOMM, "lcdOnTimeSec: " + danaRPump.lcdOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "backlightOnTimeSec: " + danaRPump.backlightOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "selectedLanguage: " + danaRPump.selectedLanguage)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaRPump.units == DanaRPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "shutdownHour: " + danaRPump.shutdownHour)
        aapsLogger.debug(LTag.PUMPCOMM, "lowReservoirRate: " + danaRPump.lowReservoirRate)
        aapsLogger.debug(LTag.PUMPCOMM, "refillAmount: " + danaRPump.refillAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage1: $selectableLanguage1")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage2: $selectableLanguage2")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage3: $selectableLanguage3")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage4: $selectableLanguage4")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage5: $selectableLanguage5")
    }

    override fun getFriendlyName(): String {
        return "OPTION__GET_USER_OPTION"
    }
}