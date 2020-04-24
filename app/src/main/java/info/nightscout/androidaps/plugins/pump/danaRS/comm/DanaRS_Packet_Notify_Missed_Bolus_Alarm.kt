package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag

class DanaRS_Packet_Notify_Missed_Bolus_Alarm(
    private val aapsLogger: AAPSLogger
) : DanaRS_Packet() {

    init {
        type = BleCommandUtil.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        val startHour: Int
        val startMin: Int
        val endHour: Int
        val endMin: Int
        var dataIndex = DATA_START
        var dataSize = 1
        startHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        startMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        endHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        endMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        if (endMin == 1 && endMin == endHour && startHour == endHour && startHour == startMin) failed = true
        else failed = false
        aapsLogger.debug(LTag.PUMPCOMM, "Start hour: $startHour")
        aapsLogger.debug(LTag.PUMPCOMM, "Start min: $startMin")
        aapsLogger.debug(LTag.PUMPCOMM, "End hour: $endHour")
        aapsLogger.debug(LTag.PUMPCOMM, "End min: $endMin")
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__MISSED_BOLUS_ALARM"
    }
}