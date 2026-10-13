package app.aaps.pump.danar.comm

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.database.DanaHistoryRecord
import app.aaps.pump.dana.events.EventDanaRSyncStatus
import dagger.android.HasAndroidInjector

@Suppress("SpellCheckingInspection")
open class MsgHistoryAll(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x41F2)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        try {
            val recordCode = intFromBuff(bytes, 0, 1).toByte()
            val date = dateFromBuff(bytes, 1) // 3 bytes
            val dailyBasal = intFromBuff(bytes, 4, 2) * 0.01
            val dailyBolus = intFromBuff(bytes, 6, 2) * 0.01
            //val paramByte5 = intFromBuff(bytes, 4, 1).toByte()
            //val paramByte6 = intFromBuff(bytes, 5, 1).toByte()
            val paramByte7 = intFromBuff(bytes, 6, 1).toByte()
            val paramByte8 = intFromBuff(bytes, 7, 1).toByte()
            val value = intFromBuff(bytes, 8, 2).toDouble()
            val danaHistoryRecord = DanaHistoryRecord(
                timestamp = date,
                code = recordCode
            )
            var messageType = ""
            when (recordCode) {
                RecordTypes.RECORD_TYPE_BOLUS     -> {
                    val datetime = dateTimeFromBuff(bytes, 1) // 5 bytes
                    danaHistoryRecord.timestamp = datetime
                    when (0xF0 and paramByte8.toInt()) {
                        0xA0 -> {
                            danaHistoryRecord.bolusType = "DS"
                            messageType += "DS bolus"
                        }

                        0xC0 -> {
                            danaHistoryRecord.bolusType = "E"
                            messageType += "E bolus"
                        }

                        0x80 -> {
                            danaHistoryRecord.bolusType = "S"
                            messageType += "S bolus"
                        }

                        0x90 -> {
                            danaHistoryRecord.bolusType = "DE"
                            messageType += "DE bolus"
                        }

                        else -> danaHistoryRecord.bolusType = "None"
                    }
                    danaHistoryRecord.duration = T.mins((paramByte8.toInt() and 0x0F) * 60 + paramByte7.toLong()).msecs()
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_DAILY     -> {
                    messageType += "dailyinsulin"
                    danaHistoryRecord.timestamp = date
                    danaHistoryRecord.dailyBasal = dailyBasal
                    danaHistoryRecord.dailyBolus = dailyBolus
                }

                RecordTypes.RECORD_TYPE_PRIME     -> {
                    messageType += "prime"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_ERROR     -> {
                    messageType += "error"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_REFILL    -> {
                    messageType += "refill"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_BASALHOUR -> {
                    messageType += "basal hour"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_TB        -> {
                    messageType += "tb"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_GLUCOSE   -> {
                    messageType += "glucose"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value
                }

                RecordTypes.RECORD_TYPE_CARBO     -> {
                    messageType += "carbo"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    danaHistoryRecord.value = value
                }

                RecordTypes.RECORD_TYPE_ALARM     -> {
                    messageType += "alarm"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    var strAlarm = "None"
                    when (paramByte8.toInt()) {
                        67 -> strAlarm = "Check"
                        79 -> strAlarm = "Occlusion"
                        66 -> strAlarm = "Low Battery"
                        83 -> strAlarm = "Shutdown"
                    }
                    danaHistoryRecord.alarm = strAlarm
                    danaHistoryRecord.value = value * 0.01
                }

                RecordTypes.RECORD_TYPE_SUSPEND   -> {
                    messageType += "suspend"
                    val datetimewihtsec = dateTimeSecFromBuff(bytes, 1) // 6 bytes
                    danaHistoryRecord.timestamp = datetimewihtsec
                    var strRecordValue = "Off"
                    if (paramByte8.toInt() == 79) strRecordValue = "On"
                    danaHistoryRecord.stringValue = strRecordValue
                }

                17.toByte()                       -> failed = true
            }
            danaHistoryRecordDao.createOrUpdate(danaHistoryRecord)
            if (recordCode == RecordTypes.RECORD_TYPE_DAILY)
                pumpSync.createOrUpdateTotalDailyDose(date, dailyBolus, dailyBasal, dailyBolus + dailyBasal, date, activePlugin.activePump.model(), danaPump.serialNumber)
            rxBus.send(EventDanaRSyncStatus(dateUtil.dateAndTimeString(danaHistoryRecord.timestamp) + " " + messageType))
        } catch (e: Exception) {
            // DanaR id sometimes producing invalid date in history
            // ignore these records
            aapsLogger.error(e.stackTraceToString())
            return
        }
    }
}