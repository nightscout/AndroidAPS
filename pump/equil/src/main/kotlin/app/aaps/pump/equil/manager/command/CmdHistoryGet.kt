package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils
import java.util.Calendar

class CmdHistoryGet(
    var currentIndex: Int,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    private val dateUtil: DateUtil,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    private var battery = 0
    private var medicine = 0
    private var rate = 0
    private var largeRate = ""
    private var timestamp = 0L
    private var index = 0

    //    private int port;
    private var type = 0
    private var level = 0
    private var parm = 0
    private var resultIndex = 0

    init {
        this.port = "0505"
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x02, 0x01)
        val data3 = Utils.intToBytes(currentIndex)
        val data = Utils.concat(indexByte, data2, data3)
        pumpReqIndex++
        aapsLogger.debug(LTag.PUMPBTCOMM, "getReqData2===" + Utils.bytesToHex(data))
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x01, 0x01)
        aapsLogger.debug(LTag.PUMPBTCOMM, "currentIndex===$currentIndex")
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
//        67631679050017070e101319
        //val index1 = data[4].toInt()
        val year = data[6].toInt() and 0xff
        val month = data[7].toInt() and 0xff
        val day = data[8].toInt() and 0xff
        val hour = data[9].toInt() and 0xff
        val min = data[10].toInt() and 0xff
        val second = data[11].toInt() and 0xff
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, min)
        calendar.set(Calendar.SECOND, second)
        calendar.set(Calendar.MILLISECOND, 0)
        timestamp = calendar.timeInMillis
        //a5e207590501 17070e100f161100000000007d0204080000
        //ae6ae9100501 17070e100f16 1100000000007d0204080000
        battery = data[12].toInt() and 0xff
        medicine = data[13].toInt() and 0xff
        rate = Utils.bytesToInt(data[15], data[14])
        //largeRate = Utils.bytesToInt(data[17], data[16])
        largeRate = Utils.bytesToHex(byteArrayOf(data[16], data[17]))
        index = Utils.bytesToInt(data[19], data[18])
        //        port = data[20] & 0xff;
        type = data[21].toInt() and 0xff
        level = data[22].toInt() and 0xff
        parm = data[23].toInt() and 0xff
        if (currentIndex != 0) equilManager.decodeHistory(data)
        resultIndex = index
        aapsLogger.debug(LTag.PUMPCOMM, this.toString())
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun toString(): String =
        "CmdHistoryGet{battery=$battery, medicine=$medicine, rate=$rate, largeRate=$largeRate, date=${dateUtil.dateAndTimeAndSecondsString(timestamp)}, index=$index, port=$port, type=$type, level=$level, parm=$parm}"

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}
