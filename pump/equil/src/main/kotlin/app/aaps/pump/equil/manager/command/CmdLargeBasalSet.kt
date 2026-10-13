package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdLargeBasalSet(
    var insulin: Double,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var step: Int = 0
    var stepTime: Int = 0

    init {
        if (insulin != 0.0) {
            step = (insulin / 0.05 * 8).toInt()
            stepTime = (insulin / 0.05 * 2).toInt()
        }
    }

    override fun getFirstData(): ByteArray {
        aapsLogger.debug(LTag.PUMPCOMM, "step===$step=====$stepTime")
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x03)
        val data3 = Utils.intToBytes(step)
        val data4 = Utils.intToBytes(stepTime)
        val data5 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3, data4, data5, data5)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x03, 0x01)
        val data3 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        // val index = data[4].toInt()
        // val status = data[6].toInt() and 0xff
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? =
        if (insulin == 0.0) EquilHistoryRecord.EventType.CANCEL_BOLUS
        else EquilHistoryRecord.EventType.SET_BOLUS
}
