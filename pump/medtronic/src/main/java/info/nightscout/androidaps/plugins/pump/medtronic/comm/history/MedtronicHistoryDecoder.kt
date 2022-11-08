package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.pump.core.utils.StringUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import org.apache.commons.lang3.StringUtils

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
abstract class MedtronicHistoryDecoder<T : MedtronicHistoryEntry?>(var aapsLogger: AAPSLogger,
                                                                   var medtronicUtil: MedtronicUtil,
                                                                   var bitUtils: ByteUtil
) : MedtronicHistoryDecoderInterface<T> {

    // STATISTICS (remove at later time or not)
    protected var statisticsEnabled = true
    protected var unknownOpCodes: MutableMap<Int, Int?> = mutableMapOf()
    protected var mapStatistics: MutableMap<RecordDecodeStatus, MutableMap<String, String>> = mutableMapOf()

    abstract fun postProcess()
    protected abstract fun runPostDecodeTasks()

    // TODO_ extend this to also use bigger pages (for now we support only 1024 pages)
    @Throws(RuntimeException::class)
    private fun checkPage(page: RawHistoryPage): MutableList<Byte> {
        if (!medtronicUtil.isModelSet) {
            aapsLogger.error(LTag.PUMPCOMM, "Device Type is not defined.")
            return mutableListOf()
        }
        return if (page.data.size != 1024) {
            page.data.toMutableList()
        } else if (page.isChecksumOK) {
            page.onlyData.toMutableList()
        } else {
            mutableListOf()
        }
    }

    fun processPageAndCreateRecords(rawHistoryPage: RawHistoryPage): MutableList<T> {
        val dataClear = checkPage(rawHistoryPage)
        val records: MutableList<T> = createRecords(dataClear)
        for (record in records) {
            decodeRecord(record)
        }
        runPostDecodeTasks()
        return records
    }

    protected fun prepareStatistics() {
        if (!statisticsEnabled) return
        // unknownOpCodes = HashMap()
        // mapStatistics = HashMap()
        for (stat in RecordDecodeStatus.values()) {
            mapStatistics[stat] = hashMapOf()
            //(mapStatistics as HashMap<RecordDecodeStatus, MutableMap<String, String>>)[stat] = hashMapOf()
        }
    }

    protected fun addToStatistics(pumpHistoryEntry: MedtronicHistoryEntryInterface, status: RecordDecodeStatus, opCode: Int?) {
        if (!statisticsEnabled) return
        if (opCode != null) {
            if (!unknownOpCodes.containsKey(opCode)) {
                unknownOpCodes[opCode] = opCode
            }
            return
        }
        if (!mapStatistics[status]!!.containsKey(pumpHistoryEntry.entryTypeName)) {
            mapStatistics[status]!!.put(pumpHistoryEntry.entryTypeName, "")
        }
    }

    protected fun showStatistics() {
        var sb = StringBuilder()
        for ((key) in unknownOpCodes) {
            StringUtil.appendToStringBuilder(sb, "" + key, ", ")
        }
        aapsLogger.info(LTag.PUMPCOMM, "STATISTICS OF PUMP DECODE")
        if (unknownOpCodes.size > 0) {
            aapsLogger.warn(LTag.PUMPCOMM, "Unknown Op Codes: $sb")
        }
        for ((key, value) in mapStatistics) {
            sb = StringBuilder()
            if (key !== RecordDecodeStatus.OK) {
                if (value.size == 0) continue
                for ((key1) in value) {
                    StringUtil.appendToStringBuilder(sb, key1, ", ")
                }
                val spaces = StringUtils.repeat(" ", 14 - key.name.length)
                aapsLogger.info(LTag.PUMPCOMM, "    ${key.name}$spaces - ${value.size}. Elements: $sb")
            } else {
                aapsLogger.info(LTag.PUMPCOMM, "    ${key.name}             - ${value.size}")
            }
        }
    }

    private fun getUnsignedByte(value: Byte): Int {
        return if (value < 0) value + 256 else value.toInt()
    }

    protected fun getUnsignedInt(value: Int): Int {
        return if (value < 0) value + 256 else value
    }

    protected fun getUnsignedInt(value: Byte): Int {
        return if (value < 0) value + 256 else value.toInt()
    }

    fun getFormattedFloat(value: Float, decimals: Int): String {
        return StringUtil.getFormattedValueUS(value, decimals)
    }

}