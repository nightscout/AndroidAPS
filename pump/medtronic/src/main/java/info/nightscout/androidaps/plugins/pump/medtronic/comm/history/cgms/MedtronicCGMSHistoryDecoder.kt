package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms

import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RecordDecodeStatus
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms.CGMSHistoryEntryType.Companion.getByCode
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import org.joda.time.LocalDateTime
import java.util.Arrays

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
class MedtronicCGMSHistoryDecoder constructor(
    aapsLogger: AAPSLogger,
    medtronicUtil: MedtronicUtil,
    bitUtils: ByteUtil
) : MedtronicHistoryDecoder<CGMSHistoryEntry>(aapsLogger, medtronicUtil, bitUtils) {

    override fun decodeRecord(record: CGMSHistoryEntry): RecordDecodeStatus? {
        return try {
            decodeRecordInternal(record)
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "     Error decoding: type={}, ex={}", record.entryType.name, ex.message, ex)
            RecordDecodeStatus.Error
        }
    }

    fun decodeRecordInternal(entry: CGMSHistoryEntry): RecordDecodeStatus {
        if (entry.dateTimeLength > 0) {
            parseDate(entry)
        }
        when (entry.entryType) {
            CGMSHistoryEntryType.SensorPacket      -> decodeSensorPacket(entry)
            CGMSHistoryEntryType.SensorError       -> decodeSensorError(entry)
            CGMSHistoryEntryType.SensorDataLow     -> decodeDataHighLow(entry, 40)
            CGMSHistoryEntryType.SensorDataHigh    -> decodeDataHighLow(entry, 400)
            CGMSHistoryEntryType.SensorTimestamp   -> decodeSensorTimestamp(entry)
            CGMSHistoryEntryType.SensorCal         -> decodeSensorCal(entry)
            CGMSHistoryEntryType.SensorCalFactor   -> decodeSensorCalFactor(entry)
            CGMSHistoryEntryType.SensorSync        -> decodeSensorSync(entry)
            CGMSHistoryEntryType.SensorStatus      -> decodeSensorStatus(entry)
            CGMSHistoryEntryType.CalBGForGH        -> decodeCalBGForGH(entry)
            CGMSHistoryEntryType.GlucoseSensorData -> decodeGlucoseSensorData(entry)

            CGMSHistoryEntryType.BatteryChange,
            CGMSHistoryEntryType.Something10,
            CGMSHistoryEntryType.DateTimeChange    -> {
            }

            CGMSHistoryEntryType.Something19,
            CGMSHistoryEntryType.DataEnd,
            CGMSHistoryEntryType.SensorWeakSignal  -> {
            }

            CGMSHistoryEntryType.UnknownOpCode,
            CGMSHistoryEntryType.None              -> {
            }
        }
        return RecordDecodeStatus.NotSupported
    }

    override fun postProcess() {}

    override fun createRecords(dataClearInput: MutableList<Byte>): MutableList<CGMSHistoryEntry> {
        dataClearInput.reverse()
        val dataClear = dataClearInput //reverseList(dataClearInput, Byte::class.java)
        prepareStatistics()
        var counter = 0
        val outList: MutableList<CGMSHistoryEntry> = mutableListOf()

        // create CGMS entries (without dates)
        do {
            val opCode = getUnsignedInt(dataClear[counter])
            counter++
            var entryType: CGMSHistoryEntryType?
            if (opCode == 0) {
                // continue;
            } else if (opCode > 0 && opCode < 20) {
                entryType = getByCode(opCode)
                if (entryType === CGMSHistoryEntryType.None) {
                    unknownOpCodes[opCode] = opCode
                    aapsLogger.warn(LTag.PUMPCOMM, "GlucoseHistoryEntry with unknown code: $opCode")
                    val pe = CGMSHistoryEntry()
                    pe.setEntryType(CGMSHistoryEntryType.None)
                    pe.opCode = opCode.toByte()
                    pe.setData(Arrays.asList(opCode.toByte()), false)
                    outList.add(pe)
                } else {
                    // System.out.println("OpCode: " + opCode);
                    val listRawData: MutableList<Byte> = ArrayList()
                    listRawData.add(opCode.toByte())
                    for (j in 0 until entryType.totalLength - 1) {
                        listRawData.add(dataClear[counter])
                        counter++
                    }
                    val pe = CGMSHistoryEntry()
                    pe.setEntryType(entryType)
                    pe.opCode = opCode.toByte()
                    pe.setData(listRawData, false)

                    // System.out.println("Record: " + pe);
                    outList.add(pe)
                }
            } else {
                val pe = CGMSHistoryEntry()
                pe.setEntryType(CGMSHistoryEntryType.GlucoseSensorData)
                pe.setData(Arrays.asList(opCode.toByte()), false)
                outList.add(pe)
            }
        } while (counter < dataClear.size)
        outList.reverse()
        val reversedOutList = outList  // reverseList(outList, CGMSHistoryEntry::class.java)
        //var timeStamp: Long? = null
        var dateTime: LocalDateTime? = null
        var getIndex = 0
        for (entry in reversedOutList) {
            decodeRecord(entry)
            if (entry.hasTimeStamp()) {
                //timeStamp = entry.atechDateTime
                dateTime = DateTimeUtil.toLocalDateTime(entry.atechDateTime)
                getIndex = 0
            } else if (entry.entryType == CGMSHistoryEntryType.GlucoseSensorData) {
                getIndex++
                if (dateTime != null) entry.setDateTime(dateTime, getIndex)
            } else {
                if (dateTime != null) entry.setDateTime(dateTime, getIndex)
            }
            aapsLogger.debug(LTag.PUMPCOMM, "Record: {}", entry)
        }
        return reversedOutList
    }

    // private fun <E> reverseList(dataClearInput: List<E>, clazz: Class<E>): List<E> {
    //     val outList: MutableList<E> = ArrayList()
    //     for (i in dataClearInput.size - 1 downTo 1) {
    //         outList.add(dataClearInput[i])
    //     }
    //     return outList
    // }

    private fun parseMinutes(one: Int): Int {
        return one and "0111111".toInt(2)
    }

    private fun parseHours(one: Int): Int {
        return one and 0x1F
    }

    private fun parseDay(one: Int): Int {
        return one and 0x1F
    }

    private fun parseMonths(first_byte: Int, second_byte: Int): Int {
        val first_two_bits = first_byte shr 6
        val second_two_bits = second_byte shr 6
        return (first_two_bits shl 2) + second_two_bits
    }

    private fun parseYear(year: Int): Int {
        return (year and 0x0F) + 2000
    }

    private fun parseDate(entry: CGMSHistoryEntry): Long? {
        if (!entry.entryType.hasDate()) return null
        val data = entry.datetime
        return if (entry.entryType.dateType === CGMSHistoryEntryType.DateType.MinuteSpecific) {
            val atechDateTime = DateTimeUtil.toATechDate(parseYear(data[3].toInt()), parseMonths(data[0].toInt(), data[1].toInt()),
                                                                                                       parseDay(data[2].toInt()), parseHours(data[0].toInt()), parseMinutes(data[1].toInt()), 0)
            entry.atechDateTime = atechDateTime
            atechDateTime
        } else if (entry.entryType.dateType === CGMSHistoryEntryType.DateType.SecondSpecific) {
            aapsLogger.warn(LTag.PUMPCOMM, "parseDate for SecondSpecific type is not implemented.")
            throw RuntimeException()
            // return null;
        } else null
    }

    private fun decodeGlucoseSensorData(entry: CGMSHistoryEntry) {
        val sgv = entry.getUnsignedRawDataByIndex(0) * 2
        entry.addDecodedData("sgv", sgv)
    }

    private fun decodeCalBGForGH(entry: CGMSHistoryEntry) {
        val amount: Int = entry.getRawDataByIndex(3).toInt() and 32 shl 3 or entry.getRawDataByIndexInt(5)
        //
        val originType: String = when (entry.getRawDataByIndexInt(3) shr 5 and 3) {
            0x00 -> "rf"
            else -> "unknown"
        }
        entry.addDecodedData("amount", amount)
        entry.addDecodedData("originType", originType)
    }

    private fun decodeSensorSync(entry: CGMSHistoryEntry) {
        val syncType: String
        syncType = when (entry.getRawDataByIndexInt(3) shr 5 and 3) {
            0x01 -> "new"
            0x02 -> "old"
            else -> "find"
        }
        entry.addDecodedData("syncType", syncType)
    }

    private fun decodeSensorStatus(entry: CGMSHistoryEntry) {
        val statusType: String
        statusType = when (entry.getRawDataByIndexInt(3) shr 5 and 3) {
            0x00 -> "off"
            0x01 -> "on"
            0x02 -> "lost"
            else -> "unknown"
        }
        entry.addDecodedData("statusType", statusType)
    }

    private fun decodeSensorCalFactor(entry: CGMSHistoryEntry) {
        val factor: Double = (entry.getRawDataByIndexInt(5) shl 8 or entry.getRawDataByIndexInt(6)) / 1000.0
        entry.addDecodedData("factor", factor)
    }

    private fun decodeSensorCal(entry: CGMSHistoryEntry) {
        val calibrationType: String
        calibrationType = when (entry.getRawDataByIndexInt(1)) {
            0x00 -> "meter_bg_now"
            0x01 -> "waiting"
            0x02 -> "cal_error"
            else -> "unknown"
        }
        entry.addDecodedData("calibrationType", calibrationType)
    }

    private fun decodeSensorTimestamp(entry: CGMSHistoryEntry) {
        val sensorTimestampType: String
        sensorTimestampType = when (entry.getRawDataByIndex(3).toInt() shr 5 and 3) {
            0x00 -> "LastRf"
            0x01 -> "PageEnd"
            0x02 -> "Gap"
            else -> "Unknown"
        }
        entry.addDecodedData("sensorTimestampType", sensorTimestampType)
    }

    private fun decodeSensorPacket(entry: CGMSHistoryEntry) {
        val packetType: String
        packetType = when (entry.getRawDataByIndex(1)) {
            0x02.toByte() -> "init"
            else          -> "unknown"
        }
        entry.addDecodedData("packetType", packetType)
    }

    private fun decodeSensorError(entry: CGMSHistoryEntry) {
        val errorType: String
        errorType = when (entry.getRawDataByIndexInt(1)) {
            0x01 -> "end"
            else -> "unknown"
        }
        entry.addDecodedData("errorType", errorType)
    }

    private fun decodeDataHighLow(entry: CGMSHistoryEntry, sgv: Int) {
        entry.addDecodedData("sgv", sgv)
    }

    override fun runPostDecodeTasks() {
        showStatistics()
    }

}