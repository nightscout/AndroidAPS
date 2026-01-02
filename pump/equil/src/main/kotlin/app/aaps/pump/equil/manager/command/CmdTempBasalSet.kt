package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdTempBasalSet(
    var insulin: Double, var duration: Int,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var step: Int = 0
    var pumpTime: Int
    var cancel: Boolean = false

    init {
        step = if (insulin != 0.0) (insulin / 0.05 * 8).toInt() / 2
        else 0
        this.pumpTime = duration * 60
    }

    override fun getFirstData(): ByteArray {
        aapsLogger.debug(LTag.PUMPCOMM, "step===$step=====$pumpTime")
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x04)
        val data3 = Utils.intToBytes(step)
        val data4 = Utils.intToBytes(pumpTime)
        val data = Utils.concat(indexByte, data2, data3, data4)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x04, 0x01)
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
        if (cancel) EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL
        else EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL
}
