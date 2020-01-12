package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_Bolus_Get_Initial_Bolus(
    private val aapsLogger: AAPSLogger
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val initialBolusValue01: Double
        val initialBolusValue02: Double
        val initialBolusValue03: Double
        val initialBolusValue04: Double
        var dataIndex = DATA_START
        var dataSize = 2
        initialBolusValue01 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        initialBolusValue02 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        initialBolusValue03 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        initialBolusValue04 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        failed = initialBolusValue01 == 0.0 && initialBolusValue02 == 0.0 && initialBolusValue03 == 0.0 && initialBolusValue04 == 0.0
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount 01: $initialBolusValue01")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount 02: $initialBolusValue02")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount 03: $initialBolusValue03")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount 04: $initialBolusValue04")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_BOLUS_RATE"
    }
}