package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

class DanaRS_Packet_General_Get_More_Information(
    private val aapsLogger: AAPSLogger,
    private val danaRPump: DanaRPump,
    private val dateUtil: DateUtil
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 15) {
            failed = true
            return
        }
        var dataIndex = DATA_START
        var dataSize = 2
        danaRPump.iob = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        dataIndex += dataSize
        dataSize = 2
        danaRPump.dailyTotalUnits = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaRPump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01
        dataIndex += dataSize
        dataSize = 2
        danaRPump.extendedBolusRemainingMinutes = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        //val remainRate = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        val lastBolusTime = Date() // it doesn't provide day only hour+min, workaround: expecting today
        dataIndex += dataSize
        dataSize = 1
        lastBolusTime.hours = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        lastBolusTime.minutes = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaRPump.lastBolusAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize)).toDouble()
        // On DanaRS DailyUnits can't be more than 160
        if (danaRPump.dailyTotalUnits > 160) failed = true
        aapsLogger.debug(LTag.PUMPCOMM, "Daily total units: " + danaRPump.dailyTotalUnits.toString() + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Is extended in progress: " + danaRPump.isExtendedInProgress)
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus remaining minutes: " + danaRPump.extendedBolusRemainingMinutes)
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus time: " + dateUtil.dateAndTimeAndSecondsString(lastBolusTime.time))
        aapsLogger.debug(LTag.PUMPCOMM, "Last bolus amount: " + danaRPump.lastBolusAmount)
    }

    override fun getFriendlyName(): String {
        return "REVIEW__GET_MORE_INFORMATION"
    }
}