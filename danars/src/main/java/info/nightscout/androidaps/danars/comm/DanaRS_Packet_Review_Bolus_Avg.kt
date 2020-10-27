package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.danars.encryption.BleEncryption

class DanaRS_Packet_Review_Bolus_Avg(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 2
        val bolusAvg03 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        val bolusAvg07 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        val bolusAvg14 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        val bolusAvg21 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 2
        val bolusAvg28 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        val required = ((1 and 0x000000FF shl 8) + (1 and 0x000000FF)) / 100.0
        if (bolusAvg03 == bolusAvg07 && bolusAvg07 == bolusAvg14 && bolusAvg14 == bolusAvg21 && bolusAvg21 == bolusAvg28 && bolusAvg28 == required) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus average 3d: $bolusAvg03 U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus average 7d: $bolusAvg07 U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus average 14d: $bolusAvg14 U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus average 21d: $bolusAvg21 U")
        aapsLogger.debug(LTag.PUMPCOMM, "Bolus average 28d: $bolusAvg28 U")
    }

    override fun getFriendlyName(): String {
        return "REVIEW__BOLUS_AVG"
    }
}