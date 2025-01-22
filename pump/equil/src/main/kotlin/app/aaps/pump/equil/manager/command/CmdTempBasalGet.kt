package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdTempBasalGet(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var time = 0
    private var step = 0

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x04)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x04, 0x02)
        val data3 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        //val index = data[4].toInt()
        step = Utils.bytes2Int(byteArrayOf(data[6], data[7], data[8], data[9]))
        time = Utils.bytes2Int(byteArrayOf(data[10], data[11], data[12], data[13]))
        aapsLogger.debug(LTag.PUMPCOMM, "CmdTempBasalGet===$step====$time")
        //        Utils.by
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}
