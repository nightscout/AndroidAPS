package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdInsulinChange(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x06)
        val data3 = Utils.intToBytes(32000)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x06, 0x01)
        val data3 = Utils.intToBytes(0)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
//        byte[] byteData = Crc.hexStringToBytes(data);
        val status = data[6].toInt() and 0xff
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
        equilManager.setInsulinChange(status)
        aapsLogger.debug(LTag.PUMPCOMM, "status====" + status + "====" + Utils.bytesToHex(data))
    }

    override fun getEventType(): EquilHistoryRecord.EventType = EquilHistoryRecord.EventType.CHANGE_INSULIN
}
