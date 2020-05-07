package info.nightscout.androidaps.plugins.pump.danaRS.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.db.DanaRHistoryRecord
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus
import info.nightscout.androidaps.utils.DateUtil
import java.util.*

abstract class DanaRS_Packet_History_(
    protected val aapsLogger: AAPSLogger,
    protected val rxBus: RxBusWrapper,
    protected val dateUtil: DateUtil,
    protected val from: Long
) : DanaRS_Packet() {

    protected var year = 0
    protected var month = 0
    protected var day = 0
    protected var hour = 0
    protected var min = 0
    protected var sec = 0

    var done = false
    var totalCount = 0

    init {
        val cal = GregorianCalendar()
        if (from != 0L) cal.timeInMillis = from
        else cal[2000, 0, 1, 0, 0] = 0
        year = cal[Calendar.YEAR] - 1900 - 100
        month = cal[Calendar.MONTH] + 1
        day = cal[Calendar.DAY_OF_MONTH]
        hour = cal[Calendar.HOUR_OF_DAY]
        min = cal[Calendar.MINUTE]
        sec = cal[Calendar.SECOND]
        aapsLogger.debug(LTag.PUMPCOMM, "Loading event history from: " + dateUtil.dateAndTimeString(cal.timeInMillis))
    }

    override fun getRequestParams(): ByteArray {
        val request = ByteArray(6)
        request[0] = (year and 0xff).toByte()
        request[1] = (month and 0xff).toByte()
        request[2] = (day and 0xff).toByte()
        request[3] = (hour and 0xff).toByte()
        request[4] = (min and 0xff).toByte()
        request[5] = (sec and 0xff).toByte()
        return request
    }

    override fun handleMessage(data: ByteArray) {
        val error: Int
        totalCount = 0
        if (data.size == 3) {
            val dataIndex = DATA_START
            val dataSize = 1
            error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
            done = true
            aapsLogger.debug(LTag.PUMPCOMM, "History end. Code: " + error + " Success: " + (error == 0x00))
        } else if (data.size == 5) {
            var dataIndex = DATA_START
            var dataSize = 1
            error = byteArrayToInt(getBytes(data, dataIndex, dataSize))
            done = true
            dataIndex += dataSize
            dataSize = 2
            totalCount = byteArrayToInt(getBytes(data, dataIndex, dataSize))
            aapsLogger.debug(LTag.PUMPCOMM, "History end. Code: " + error + " Success: " + (error == 0x00) + " Toatal count: " + totalCount)
        } else {
            val recordCode = byteArrayToInt(getBytes(data, DATA_START, 1))
            val historyYear = byteArrayToInt(getBytes(data, DATA_START + 1, 1))
            val historyMonth = byteArrayToInt(getBytes(data, DATA_START + 2, 1))
            val historyDay = byteArrayToInt(getBytes(data, DATA_START + 3, 1))
            val historyHour = byteArrayToInt(getBytes(data, DATA_START + 4, 1))
            val dailyBasal: Double = ((data[DATA_START + 4].toInt() and 0xFF shl 8) + (data[DATA_START + 5].toInt() and 0xFF)) * 0.01
            val historyMinute = byteArrayToInt(getBytes(data, DATA_START + 5, 1))
            val historySecond = byteArrayToInt(getBytes(data, DATA_START + 6, 1))
            val paramByte7 = historySecond.toByte()
            val dailyBolus: Double = ((data[DATA_START + 6].toInt() and 0xFF shl 8) + (data[DATA_START + 7].toInt() and 0xFF)) * 0.01
            val date = Date(100 + historyYear, historyMonth - 1, historyDay)
            val datetime = Date(100 + historyYear, historyMonth - 1, historyDay, historyHour, historyMinute)
            val datetimewihtsec = Date(100 + historyYear, historyMonth - 1, historyDay, historyHour, historyMinute, historySecond)
            val historyCode = byteArrayToInt(getBytes(data, DATA_START + 7, 1))
            val paramByte8 = historyCode.toByte()
            val value: Int = (data[DATA_START + 8].toInt() and 0xFF shl 8) + (data[DATA_START + 9].toInt() and 0xFF)
            aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec) + " Code: " + historyCode + " Value: " + value)
            val danaRHistoryRecord = DanaRHistoryRecord()
            danaRHistoryRecord.setBytes(data)
            // danaRHistoryRecord.recordCode is different from DanaR codes
// set in switch for every type
            var messageType = ""
            when (recordCode) {
                0x02 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_BOLUS
                    danaRHistoryRecord.recordDate = datetime.time
                    when (0xF0 and paramByte8.toInt()) {
                        0xA0 -> {
                            danaRHistoryRecord.bolusType = "DS"
                            messageType += "DS bolus"
                        }

                        0xC0 -> {
                            danaRHistoryRecord.bolusType = "E"
                            messageType += "E bolus"
                        }

                        0x80 -> {
                            danaRHistoryRecord.bolusType = "S"
                            messageType += "S bolus"
                        }

                        0x90 -> {
                            danaRHistoryRecord.bolusType = "DE"
                            messageType += "DE bolus"
                        }

                        else -> danaRHistoryRecord.bolusType = "None"
                    }
                    danaRHistoryRecord.recordDuration = (paramByte8.toInt() and 0x0F) * 60 + paramByte7.toInt()
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x03 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_DAILY
                    messageType += "dailyinsulin"
                    danaRHistoryRecord.recordDate = date.time
                    danaRHistoryRecord.recordDailyBasal = dailyBasal
                    danaRHistoryRecord.recordDailyBolus = dailyBolus
                }

                0x04 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_PRIME
                    messageType += "prime"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x05 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_REFILL
                    messageType += "refill"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x0b -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_BASALHOUR
                    messageType += "basal hour"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x99 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_TEMP_BASAL
                    messageType += "tb"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x06 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_GLUCOSE
                    messageType += "glucose"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value.toDouble()
                }

                0x07 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_CARBO
                    messageType += "carbo"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    danaRHistoryRecord.recordValue = value.toDouble()
                }

                0x0a -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_ALARM
                    messageType += "alarm"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    var strAlarm = "None"
                    when (paramByte8.toInt()) {
                        67 -> strAlarm = "Check"
                        79 -> strAlarm = "Occlusion"
                        66 -> strAlarm = "Low Battery"
                        83 -> strAlarm = "Shutdown"
                    }
                    danaRHistoryRecord.recordAlarm = strAlarm
                    danaRHistoryRecord.recordValue = value * 0.01
                }

                0x09 -> {
                    danaRHistoryRecord.recordCode = RecordTypes.RECORD_TYPE_SUSPEND
                    messageType += "suspend"
                    danaRHistoryRecord.recordDate = datetimewihtsec.time
                    var strRecordValue = "Off"
                    if (paramByte8.toInt() == 79) strRecordValue = "On"
                    danaRHistoryRecord.stringRecordValue = strRecordValue
                }
            }
            MainApp.getDbHelper().createOrUpdate(danaRHistoryRecord)
            rxBus.send(EventDanaRSyncStatus(dateUtil.dateAndTimeString(danaRHistoryRecord.recordDate) + " " + messageType))
        }
    }

}