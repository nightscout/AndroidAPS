package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils
import java.util.Calendar

class CmdTimeSet(
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    init {
        port = "0505"
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x01, 0x00)
        val data3 = ByteArray(6)
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 2000
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        data3[0] = year.toByte()
        data3[1] = month.toByte()
        data3[2] = day.toByte()
        data3[3] = hour.toByte()
        data3[4] = minute.toByte()
        data3[5] = second.toByte()
        val data = Utils.concat(indexByte, data2, data3)
        //        ZLog.e2("setTime==" + year + "==" + month + "===" + day + "===" + hour + "===" + minute + "===" + second);
        pumpReqIndex++
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x00, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun getEventType(): EquilHistoryRecord.EventType? = EquilHistoryRecord.EventType.SET_TIME
}
