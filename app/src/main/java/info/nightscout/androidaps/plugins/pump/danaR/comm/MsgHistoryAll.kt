package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.db.DanaRHistoryRecord
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.danaR.events.EventDanaRSyncStatus
import info.nightscout.androidaps.utils.DateUtil

open class MsgHistoryAll(
    val aapsLogger: AAPSLogger,
    val rxBus: RxBusWrapper,
    private val dateUtil: DateUtil
) : MessageBase() {

    init {
        SetCommand(0x41F2)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        val recordCode = intFromBuff(bytes, 0, 1).toByte()
        val date = dateFromBuff(bytes, 1) // 3 bytes
        val datetime = dateTimeFromBuff(bytes, 1) // 5 bytes
        val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
        val dailyBasal = intFromBuff(bytes, 4, 2) * 0.01
        val dailyBolus = intFromBuff(bytes, 6, 2) * 0.01
        val paramByte5 = intFromBuff(bytes, 4, 1).toByte()
        val paramByte6 = intFromBuff(bytes, 5, 1).toByte()
        val paramByte7 = intFromBuff(bytes, 6, 1).toByte()
        val paramByte8 = intFromBuff(bytes, 7, 1).toByte()
        val value = intFromBuff(bytes, 8, 2).toDouble()
        val danaRHistoryRecord = DanaRHistoryRecord()
        danaRHistoryRecord.recordCode = recordCode
        danaRHistoryRecord.setBytes(bytes)
        var messageType = ""
        when (recordCode) {
            RecordTypes.RECORD_TYPE_BOLUS     -> {
                danaRHistoryRecord.recordDate = datetime
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

            RecordTypes.RECORD_TYPE_DAILY     -> {
                messageType += "dailyinsulin"
                danaRHistoryRecord.recordDate = date
                danaRHistoryRecord.recordDailyBasal = dailyBasal
                danaRHistoryRecord.recordDailyBolus = dailyBolus
            }

            RecordTypes.RECORD_TYPE_PRIME     -> {
                messageType += "prime"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value * 0.01
            }

            RecordTypes.RECORD_TYPE_ERROR     -> {
                messageType += "error"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value * 0.01
            }

            RecordTypes.RECORD_TYPE_REFILL    -> {
                messageType += "refill"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value * 0.01
            }

            RecordTypes.RECORD_TYPE_BASALHOUR -> {
                messageType += "basal hour"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value * 0.01
            }

            RecordTypes.RECORD_TYPE_TB        -> {
                messageType += "tb"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value * 0.01
            }

            RecordTypes.RECORD_TYPE_GLUCOSE   -> {
                messageType += "glucose"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value
            }

            RecordTypes.RECORD_TYPE_CARBO     -> {
                messageType += "carbo"
                danaRHistoryRecord.recordDate = datetimewihtsec
                danaRHistoryRecord.recordValue = value
            }

            RecordTypes.RECORD_TYPE_ALARM     -> {
                messageType += "alarm"
                danaRHistoryRecord.recordDate = datetimewihtsec
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

            RecordTypes.RECORD_TYPE_SUSPEND   -> {
                messageType += "suspend"
                danaRHistoryRecord.recordDate = datetimewihtsec
                var strRecordValue = "Off"
                if (paramByte8.toInt() == 79) strRecordValue = "On"
                danaRHistoryRecord.stringRecordValue = strRecordValue
            }

            17.toByte()                       -> failed = true
        }
        MainApp.getDbHelper().createOrUpdate(danaRHistoryRecord)
        rxBus.send(EventDanaRSyncStatus(dateUtil.dateAndTimeString(danaRHistoryRecord.recordDate) + " " + messageType))
    }
}