package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class DanaRS_Packet_Option_Set_Pump_Time(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private var time: Long = 0
) : DanaRS_Packet() {

    var error = 0

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME
        aapsLogger.debug(LTag.PUMPCOMM, "Setting pump time " + dateUtil.dateAndTimeString(time))
    }

    override fun getRequestParams(): ByteArray {
        val date = Date(time)
        val request = ByteArray(6)
        request[0] = (date.year - 100 and 0xff).toByte()
        request[1] = (date.month + 1 and 0xff).toByte()
        request[2] = (date.date and 0xff).toByte()
        request[3] = (date.hours and 0xff).toByte()
        request[4] = (date.minutes and 0xff).toByte()
        request[5] = (date.seconds and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
    }

    override fun getFriendlyName(): String {
        return "OPTION__SET_PUMP_TIME"
    }
}