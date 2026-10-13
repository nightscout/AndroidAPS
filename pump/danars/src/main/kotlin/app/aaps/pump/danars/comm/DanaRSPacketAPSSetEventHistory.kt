package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.inject.Inject

class DanaRSPacketAPSSetEventHistory @Inject constructor(
    private val aapsLogger: AAPSLogger,
    dateUtil: DateUtil,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    private var packetType: Int = 0
    private var time: Long = 0
    private var param1: Int = 0
    private var param2: Int = 0

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY
        aapsLogger.debug(LTag.PUMPCOMM, "Set history entry: " + dateUtil.dateAndTimeAndSecondsString(time) + " type: " + packetType + " param1: " + param1 + " param2: " + param2)
    }

    fun with(packetType: Int, time: Long, param1: Int, param2: Int) = this.also {
        it.packetType = packetType
        it.time = time
        it.param1 = param1
        it.param2 = param2
        if ((packetType == DanaPump.HistoryEntry.CARBS.value || packetType == DanaPump.HistoryEntry.BOLUS.value) && param1 <= 0) it.param1 = 0
    }

    override fun getRequestParams(): ByteArray {
        val date =
            if (danaPump.usingUTC) DateTime(time).withZone(DateTimeZone.UTC)
            else DateTime(time)
        val request = ByteArray(11)
        request[0] = (packetType and 0xff).toByte()
        request[1] = (date.year - 2000 and 0xff).toByte()
        request[2] = (date.monthOfYear and 0xff).toByte()
        request[3] = (date.dayOfMonth and 0xff).toByte()
        request[4] = (date.hourOfDay and 0xff).toByte()
        request[5] = (date.minuteOfHour and 0xff).toByte()
        request[6] = (date.secondOfMinute and 0xff).toByte()
        request[7] = (param1 ushr 8 and 0xff).toByte()
        request[8] = (param1 and 0xff).toByte()
        request[9] = (param2 ushr 8 and 0xff).toByte()
        request[10] = (param2 and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result != 0) {
            failed = true
            aapsLogger.error(LTag.PUMPCOMM, "Set history entry result: $result ERROR!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set history entry result: OK")
        }
    }

    override val friendlyName: String = "APS_SET_EVENT_HISTORY"
}