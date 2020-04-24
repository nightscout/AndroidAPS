package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Bolus_Get_CIR_CF_Array(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val language = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.morningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir02 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.afternoonCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir04 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.eveningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir06 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.nightCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        val cf02: Double
        val cf04: Double
        val cf06: Double
        if (danaRPump.units == DanaRPump.UNITS_MGDL) {
            dataIndex += dataSize
            dataSize = 2
            danaRPump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaRPump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaRPump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaRPump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        } else {
            dataIndex += dataSize
            dataSize = 2
            danaRPump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaRPump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaRPump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaRPump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        }
        if (danaRPump.units < 0 || danaRPump.units > 1) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Language: $language")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaRPump.units == DanaRPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CIR: " + danaRPump.morningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CF: " + danaRPump.morningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CIR: " + danaRPump.afternoonCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CF: " + danaRPump.afternoonCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CIR: " + danaRPump.eveningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CF: " + danaRPump.eveningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CIR: " + danaRPump.nightCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CF: " + danaRPump.nightCF)
        aapsLogger.debug(LTag.PUMPCOMM, "cir02: $cir02")
        aapsLogger.debug(LTag.PUMPCOMM, "cir04: $cir04")
        aapsLogger.debug(LTag.PUMPCOMM, "cir06: $cir06")
        aapsLogger.debug(LTag.PUMPCOMM, "cf02: $cf02")
        aapsLogger.debug(LTag.PUMPCOMM, "cf04: $cf04")
        aapsLogger.debug(LTag.PUMPCOMM, "cf06: $cf06")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_CIR_CF_ARRAY"
    }
}