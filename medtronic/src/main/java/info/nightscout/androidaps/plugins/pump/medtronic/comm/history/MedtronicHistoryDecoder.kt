package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import org.apache.commons.lang3.StringUtils
import java.util.*
import javax.inject.Inject
import kotlin.jvm.Throws

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
abstract class MedtronicHistoryDecoder<T : MedtronicHistoryEntry?> : MedtronicHistoryDecoderInterface<T> {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var bitUtils: ByteUtil

    // STATISTICS (remove at later time or not)
    protected var statisticsEnabled = true
    protected var unknownOpCodes: MutableMap<Int, Int?>? = null
    protected var mapStatistics: MutableMap<RecordDecodeStatus, MutableMap<String, String>>? = null

    // public abstract <E extends MedtronicHistoryEntry> Class<E> getHistoryEntryClass();
    // public abstract RecordDecodeStatus decodeRecord(T record);
    abstract fun postProcess()
    protected abstract fun runPostDecodeTasks()

    // TODO_ extend this to also use bigger pages (for now we support only 1024 pages)
    @Throws(RuntimeException::class)
    private fun checkPage(page: RawHistoryPage, partial: Boolean): List<Byte> {
        val byteList: List<Byte> = ArrayList()

        if (medtronicUtil.medtronicPumpModel == null) {
            aapsLogger.error(LTag.PUMPCOMM, "Device Type is not defined.")
            return byteList
        }
        return if (page.data.size != 1024) {
            ByteUtil.getListFromByteArray(page.data)
        } else if (page.isChecksumOK) {
            ByteUtil.getListFromByteArray(page.onlyData)
        } else {
            byteList
        }
    }

    fun processPageAndCreateRecords(rawHistoryPage: RawHistoryPage): List<T?>? {
        return processPageAndCreateRecords(rawHistoryPage, false)
    }

    protected fun prepareStatistics() {
        if (!statisticsEnabled) return
        unknownOpCodes = HashMap()
        mapStatistics = HashMap()
        for (stat in RecordDecodeStatus.values()) {
            (mapStatistics as HashMap<RecordDecodeStatus, MutableMap<String, String>>)[stat] = HashMap()
        }
    }

    protected fun addToStatistics(pumpHistoryEntry: MedtronicHistoryEntryInterface, status: RecordDecodeStatus?, opCode: Int?) {
        if (!statisticsEnabled) return
        if (opCode != null) {
            if (!unknownOpCodes!!.containsKey(opCode)) {
                unknownOpCodes!![opCode] = opCode
            }
            return
        }
        if (!mapStatistics!![status]!!.containsKey(pumpHistoryEntry.entryTypeName)) {
            mapStatistics!![status]!!.put(pumpHistoryEntry.entryTypeName!!, "")
        }
    }

    protected fun showStatistics() {
        var sb = StringBuilder()
        for ((key) in unknownOpCodes!!) {
            StringUtil.appendToStringBuilder(sb, "" + key, ", ")
        }
        aapsLogger.info(LTag.PUMPCOMM, "STATISTICS OF PUMP DECODE")
        if (unknownOpCodes!!.size > 0) {
            aapsLogger.warn(LTag.PUMPCOMM, "Unknown Op Codes: $sb")
        }
        for ((key, value) in mapStatistics!!) {
            sb = StringBuilder()
            if (key !== RecordDecodeStatus.OK) {
                if (value.size == 0) continue
                for ((key1) in value) {
                    StringUtil.appendToStringBuilder(sb, key1, ", ")
                }
                val spaces = StringUtils.repeat(" ", 14 - key.name.length)
                aapsLogger.info(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "    %s%s - %d. Elements: %s", key.name, spaces, value.size, sb.toString()))
            } else {
                aapsLogger.info(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "    %s             - %d", key.name, value.size))
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
        return StringUtil.getFormatedValueUS(value, decimals)
    }

    private fun processPageAndCreateRecords(rawHistoryPage: RawHistoryPage, partial: Boolean): List<T> {
        val dataClear = checkPage(rawHistoryPage, partial)
        val records: List<T> = createRecords(dataClear)
        for (record in records!!) {
            decodeRecord(record)
        }
        runPostDecodeTasks()
        return records
    }
}