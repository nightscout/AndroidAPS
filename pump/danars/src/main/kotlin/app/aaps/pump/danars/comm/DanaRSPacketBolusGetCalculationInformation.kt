package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBolusGetCalculationInformation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION
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
        danaPump.currentTarget = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        danaPump.currentCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.currentCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        danaPump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        if (danaPump.units == DanaPump.UNITS_MMOL) {
            danaPump.currentCF = danaPump.currentCF / 100.0
            danaPump.currentTarget = danaPump.currentTarget / 100.0
            currentBG /= 100.0
        }
        if (error != 0) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current BG: $currentBG")
        aapsLogger.debug(LTag.PUMPCOMM, "Carbs: $carbohydrate")
        aapsLogger.debug(LTag.PUMPCOMM, "Current target: " + danaPump.currentTarget)
        aapsLogger.debug(LTag.PUMPCOMM, "Current CIR: " + danaPump.currentCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current CF: " + danaPump.currentCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump IOB: " + danaPump.iob)
    }

    override val friendlyName: String = "BOLUS__GET_CALCULATION_INFORMATION"
}