package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump

class DanaRS_Packet_Bolus_Get_Dual_Bolus(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val error = byteArrayToInt(getBytes(data, DATA_START, 1))
        danaRPump.bolusStep = byteArrayToInt(getBytes(data, DATA_START + 1, 2)) / 100.0
        danaRPump.extendedBolusAbsoluteRate = byteArrayToInt(getBytes(data, DATA_START + 3, 2)) / 100.0
        danaRPump.maxBolus = byteArrayToInt(getBytes(data, DATA_START + 5, 2)) / 100.0
        val bolusIncrement = byteArrayToInt(getBytes(data, DATA_START + 7, 1)) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: ${danaRPump.bolusStep} U")
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus running: ${danaRPump.extendedBolusAbsoluteRate} U/h")
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaRPump.maxBolus + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusIncrement: $bolusIncrement U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_DUAL_BOLUS"
    }
}