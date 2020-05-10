package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class DanaRS_Packet_Bolus_Get_Step_Bolus_Information(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val dateUtil: DateUtil
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val bolusType = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.initialBolusAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        val lbt = Date() // it doesn't provide day only hour+min, workaround: expecting today
        dataIndex += dataSize
        dataSize = 1
        lbt.hours = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        lbt.minutes = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        danaRPump.lastBolusTime = lbt.time
        dataIndex += dataSize
        dataSize = 2
        danaRPump.lastBolusAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        danaRPump.maxBolus = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaRPump.bolusStep = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        failed = error != 0
        aapsLogger.debug(LTag.PUMPCOMM, "Result: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "BolusType: $bolusType")
        aapsLogger.debug(LTag.PUMPCOMM, "Initial bolus amount: " + danaRPump.initialBolusAmount + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeString(danaRPump.lastBolusTime))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaRPump.lastBolusAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "Max bolus: " + danaRPump.maxBolus + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus step: " + danaRPump.bolusStep + " U")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_STEP_BOLUS_INFORMATION"
    }
}