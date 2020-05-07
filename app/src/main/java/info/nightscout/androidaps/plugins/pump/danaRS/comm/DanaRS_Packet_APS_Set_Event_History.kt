package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class DanaRS_Packet_APS_Set_Event_History(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private var packetType: Int,
    private var time: Long,
    private var param1: Int,
    private var param2: Int
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY
        if ((packetType == DanaRPump.CARBS || packetType == DanaRPump.BOLUS) && param1 <= 0) this.param1 = 0
        aapsLogger.debug(LTag.PUMPCOMM, "Set history entry: " + dateUtil.dateAndTimeString(time) + " type: " + packetType + " param1: " + param1 + " param2: " + param2)
    }

    override fun getRequestParams(): ByteArray {
        val cal = GregorianCalendar()
        cal.timeInMillis = time
        val year = cal[Calendar.YEAR] - 1900 - 100
        val month = cal[Calendar.MONTH] + 1
        val day = cal[Calendar.DAY_OF_MONTH]
        val hour = cal[Calendar.HOUR_OF_DAY]
        val min = cal[Calendar.MINUTE]
        val sec = cal[Calendar.SECOND]
        val request = ByteArray(11)
        request[0] = (packetType and 0xff).toByte()
        request[1] = (year and 0xff).toByte()
        request[2] = (month and 0xff).toByte()
        request[3] = (day and 0xff).toByte()
        request[4] = (hour and 0xff).toByte()
        request[5] = (min and 0xff).toByte()
        request[6] = (sec and 0xff).toByte()
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
            aapsLogger.error(LTag.PUMPCOMM, "Set history entry result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set history entry result: $result")
        }
    }

    override fun getFriendlyName(): String {
        return "APS_SET_EVENT_HISTORY"
    }
}