package app.aaps.pump.danars.comm

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecord
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.dana.events.EventDanaRSyncStatus
import org.joda.time.DateTime
import java.util.Calendar
import java.util.GregorianCalendar

abstract class DanaRSPacketHistory internal constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val rxBus: RxBus,
    private val danaHistoryRecordDao: DanaHistoryRecordDao,
    private val pumpSync: PumpSync,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    protected val from: Long = 0

    protected var year = 0
    protected var month = 0
    protected var day = 0
    protected var hour = 0
    protected var min = 0
    protected var sec = 0

    var done = false
    var totalCount = 0
    val danaRHistoryRecord = DanaHistoryRecord(0)

    fun with(from: Long) = this.also {
        it.from == from
        val cal = GregorianCalendar()
        if (it.from != 0L) cal.timeInMillis = it.from
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
            aapsLogger.debug(LTag.PUMPCOMM, "History end. Code: " + error + " Success: " + (error == 0x00) + " Total count: " + totalCount)
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
            val historyCode = byteArrayToInt(getBytes(data, DATA_START + 7, 1))
            val paramByte8 = historyCode.toByte()
            val value: Int = (data[DATA_START + 8].toInt() and 0xFF shl 8) + (data[DATA_START + 9].toInt() and 0xFF)
            // danaRHistoryRecord.code is different from DanaR codes
            // set in switch for every type
            var messageType = ""
            when (recordCode) {
                0x02 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_BOLUS
                    val datetime = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute)
                    danaRHistoryRecord.timestamp = datetime.millis
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
                    danaRHistoryRecord.duration = T.mins((paramByte8.toInt() and 0x0F) * 60 + paramByte7.toLong()).msecs()
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetime.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x03 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_DAILY
                    messageType += "dailyinsulin"
                    val date = DateTime(2000 + historyYear, historyMonth, historyDay, 0, 0)
                    danaRHistoryRecord.timestamp = date.millis
                    danaRHistoryRecord.dailyBasal = dailyBasal
                    danaRHistoryRecord.dailyBolus = dailyBolus
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(date.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x04 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_PRIME
                    messageType += "prime"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x05 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_REFILL
                    messageType += "refill"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x0b -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_BASALHOUR
                    messageType += "basal hour"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x99 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_TEMP_BASAL
                    messageType += "tb"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x06 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_GLUCOSE
                    messageType += "glucose"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value.toDouble()
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x07 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_CARBO
                    messageType += "carbo"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    danaRHistoryRecord.value = value.toDouble()
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x0a -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_ALARM
                    messageType += "alarm"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    var strAlarm = "None"
                    when (paramByte8) {
                        'P'.code.toByte() -> strAlarm = "Basal Compare"
                        'R'.code.toByte() -> strAlarm = "Empty Reservoir"
                        'C'.code.toByte() -> strAlarm = "Check"
                        'O'.code.toByte() -> strAlarm = "Occlusion"
                        'M'.code.toByte() -> strAlarm = "Basal max"
                        'D'.code.toByte() -> strAlarm = "Daily max"
                        'B'.code.toByte() -> strAlarm = "Low Battery"
                        'S'.code.toByte() -> strAlarm = "Shutdown"
                    }
                    danaRHistoryRecord.alarm = strAlarm
                    danaRHistoryRecord.value = value * 0.01
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }

                0x09 -> {
                    danaRHistoryRecord.code = RecordTypes.RECORD_TYPE_SUSPEND
                    messageType += "suspend"
                    val datetimewihtsec = DateTime(2000 + historyYear, historyMonth, historyDay, historyHour, historyMinute, historySecond)
                    danaRHistoryRecord.timestamp = datetimewihtsec.millis
                    var strRecordValue = "Off"
                    if (paramByte8.toInt() == 79) strRecordValue = "On"
                    danaRHistoryRecord.stringValue = strRecordValue
                    aapsLogger.debug(LTag.PUMPCOMM, "History packet: " + recordCode + " Date: " + dateUtil.dateAndTimeString(datetimewihtsec.millis) + " Code: " + historyCode + " Value: " + value)
                }
            }
            danaHistoryRecordDao.createOrUpdate(danaRHistoryRecord)
            //If it is a TDD, store it for stats also.
            if (danaRHistoryRecord.code == RecordTypes.RECORD_TYPE_DAILY) {
                pumpSync.createOrUpdateTotalDailyDose(
                    timestamp = danaRHistoryRecord.timestamp,
                    bolusAmount = danaRHistoryRecord.dailyBolus,
                    basalAmount = danaRHistoryRecord.dailyBasal,
                    totalAmount = 0.0,
                    pumpId = null,
                    pumpType = danaPump.pumpType(),
                    danaPump.serialNumber
                )
            }
            rxBus.send(EventDanaRSyncStatus(dateUtil.dateAndTimeString(danaRHistoryRecord.timestamp) + " " + messageType))
        }
    }

}