package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import org.joda.time.DateTime
import javax.inject.Inject

class DanaRSPacketOptionGetPumpUTCAndTimeZone @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) : DanaRSPacket() {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting pump UTC time")
    }

    override fun handleMessage(data: ByteArray) {
        val year = byteArrayToInt(getBytes(data, DATA_START, 1))
        val month = byteArrayToInt(getBytes(data, DATA_START + 1, 1))
        val day = byteArrayToInt(getBytes(data, DATA_START + 2, 1))
        val hour = byteArrayToInt(getBytes(data, DATA_START + 3, 1))
        val min = byteArrayToInt(getBytes(data, DATA_START + 4, 1))
        val sec = byteArrayToInt(getBytes(data, DATA_START + 5, 1))
        val zoneOffset = getBytes(data, DATA_START + 6, 1)[0].toInt()
        val time = DateTime(2000 + year, month, day, hour, min, sec)
        danaPump.setPumpTime(time.millis, zoneOffset)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time ${dateUtil.dateAndTimeAndSecondsString(danaPump.pumpTime)} ZoneOffset: $zoneOffset")
    }

    override fun handleMessageNotReceived() {
        super.handleMessageNotReceived()
        danaPump.resetPumpTime()
    }

    override val friendlyName: String = "OPTION__GET_PUMP_UTC_AND_TIMEZONE"
}