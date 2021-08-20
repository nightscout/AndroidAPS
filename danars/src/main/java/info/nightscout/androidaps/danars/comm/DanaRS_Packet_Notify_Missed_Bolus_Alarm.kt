package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Notify_Missed_Bolus_Alarm(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM
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
        failed = endMin == 1 && endMin == endHour && startHour == endHour && startHour == startMin
        aapsLogger.debug(LTag.PUMPCOMM, "Start hour: $startHour")
        aapsLogger.debug(LTag.PUMPCOMM, "Start min: $startMin")
        aapsLogger.debug(LTag.PUMPCOMM, "End hour: $endHour")
        aapsLogger.debug(LTag.PUMPCOMM, "End min: $endMin")
    }

    override fun getFriendlyName(): String {
        return "NOTIFY__MISSED_BOLUS_ALARM"
    }
}