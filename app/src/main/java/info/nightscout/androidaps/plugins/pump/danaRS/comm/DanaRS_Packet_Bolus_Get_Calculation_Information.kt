package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Bolus_Get_Calculation_Information(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {


    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        var currentBG = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        val carbohydrate = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.currentTarget = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        danaRPump.currentCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.currentCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        danaRPump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaRPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        if (danaRPump.units == DanaRPump.UNITS_MMOL) {
            danaRPump.currentCF = danaRPump.currentCF / 100.0
            danaRPump.currentTarget = danaRPump.currentTarget / 100.0
            currentBG = currentBG / 100.0
        }
        if (error != 0) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaRPump.units == DanaRPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current BG: $currentBG")
        aapsLogger.debug(LTag.PUMPCOMM, "Carbs: $carbohydrate")
        aapsLogger.debug(LTag.PUMPCOMM, "Current target: " + danaRPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current CIR: " + danaRPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current CF: " + danaRPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump IOB: " + danaRPump.iob)
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_CALCULATION_INFORMATION"
    }
}