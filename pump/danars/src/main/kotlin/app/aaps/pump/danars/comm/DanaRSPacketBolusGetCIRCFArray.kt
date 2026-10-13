package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBolusGetCIRCFArray @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val language = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.morningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir02 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.afternoonCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir04 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.eveningCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        val cir06 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.nightCIR = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        val cf02: Double
        val cf04: Double
        val cf06: Double
        if (danaPump.units == DanaPump.UNITS_MGDL) {
            dataIndex += dataSize
            dataSize = 2
            danaPump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaPump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaPump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
            dataIndex += dataSize
            dataSize = 2
            danaPump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        } else {
            dataIndex += dataSize
            dataSize = 2
            danaPump.morningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaPump.afternoonCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaPump.eveningCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            cf06 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            dataIndex += dataSize
            dataSize = 2
            danaPump.nightCF = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        }
        if (danaPump.units < 0 || danaPump.units > 1) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Language: $language")
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CIR: " + danaPump.morningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump morning CF: " + danaPump.morningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CIR: " + danaPump.afternoonCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump afternoon CF: " + danaPump.afternoonCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CIR: " + danaPump.eveningCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump evening CF: " + danaPump.eveningCF)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CIR: " + danaPump.nightCIR)
        aapsLogger.debug(LTag.PUMPCOMM, "Current pump night CF: " + danaPump.nightCF)
        aapsLogger.debug(LTag.PUMPCOMM, "cir02: $cir02")
        aapsLogger.debug(LTag.PUMPCOMM, "cir04: $cir04")
        aapsLogger.debug(LTag.PUMPCOMM, "cir06: $cir06")
        aapsLogger.debug(LTag.PUMPCOMM, "cf02: $cf02")
        aapsLogger.debug(LTag.PUMPCOMM, "cf04: $cf04")
        aapsLogger.debug(LTag.PUMPCOMM, "cf06: $cf06")
    }

    override val friendlyName: String = "BOLUS__GET_CIR_CF_ARRAY"
}