package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Bolus_Get_Extended_Bolus_State(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaRPump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01
        dataIndex += dataSize
        dataSize = 1
        danaRPump.extendedBolusMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize)) * 30
        dataIndex += dataSize
        dataSize = 2
        danaRPump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.extendedBolusSoFarInMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.extendedBolusDeliveredSoFar = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended bolus running: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus running: " + danaRPump.extendedBolusAbsoluteRate + " U/h")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus duration: " + danaRPump.extendedBolusMinutes + " min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus so far: " + danaRPump.extendedBolusSoFarInMinutes + " min")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus delivered so far: " + danaRPump.extendedBolusDeliveredSoFar + " U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_EXTENDED_BOLUS_STATE"
    }
}