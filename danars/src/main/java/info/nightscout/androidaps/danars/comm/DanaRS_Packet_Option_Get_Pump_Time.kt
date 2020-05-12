package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import org.joda.time.DateTime
import javax.inject.Inject

class DanaRS_Packet_Option_Get_Pump_Time(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting pump time")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val year = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val month = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val day = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val hour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val min = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val sec = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        val time = DateTime(2000 + year, month, day, hour, min, sec)
        danaPump.pumpTime = time.millis
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time " + dateUtil.dateAndTimeString(time.millis))
    }

    override fun handleMessageNotReceived() {
        danaPump.pumpTime = 0
    }

    override fun getFriendlyName(): String {
        return "OPTION__GET_PUMP_TIME"
    }
}