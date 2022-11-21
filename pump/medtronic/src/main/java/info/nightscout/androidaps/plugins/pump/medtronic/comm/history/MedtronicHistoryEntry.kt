package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

import com.google.gson.annotations.Expose
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.pump.core.utils.ByteUtil
import info.nightscout.pump.core.utils.StringUtil

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
abstract class MedtronicHistoryEntry : MedtronicHistoryEntryInterface {

    var rawData: List<Byte> = listOf()

    protected var sizes = IntArray(3)

    var head: ByteArray = byteArrayOf()
    var datetime: ByteArray = byteArrayOf()
    var body: ByteArray = byteArrayOf()

    var id: Long = 0

    @Expose
    var DT: String? = null

    @Expose
    var atechDateTime: Long = 0L
        set(value) {
            field = value
            DT = DateTimeUtil.toString(value)
            if (isEntryTypeSet() && value != 0L) pumpId = generatePumpId()
        }

    @Expose
    var decodedData: MutableMap<String, Any> = mutableMapOf()

    /**
     * Pump id that will be used with AAPS object (time * 1000 + historyType (max is FF = 255)
     */
    var pumpId: Long = 0L
        get() {
            if (field == 0L) {
                field = generatePumpId()
            }
            return field
        }

    /**
     * if history object is already linked to AAPS object (either Treatment, TempBasal or TDD (tdd's
     * are not actually
     * linked))
     */
    var linked = false

    /**
     * Linked object, see linked
     */
    var linkedObject: Any? = null
        set(value) {
            linked = true
            field = value
        }

    abstract fun generatePumpId(): Long

    abstract fun isEntryTypeSet(): Boolean

    override fun setData(listRawData: MutableList<Byte>, doNotProcess: Boolean) {
        rawData = listRawData

        // System.out.println("Head: " + sizes[0] + ", dates: " + sizes[1] +
        // ", body=" + sizes[2]);
        if (!doNotProcess) {
            head = ByteArray(headLength - 1)
            for (i in 1 until headLength) {
                head[i - 1] = listRawData[i]
            }
            if (dateTimeLength > 0) {
                datetime = ByteArray(dateTimeLength)
                var i = headLength
                var j = 0
                while (j < dateTimeLength) {
                    datetime[j] = listRawData[i]
                    i++
                    j++
                }
            } else
                datetime = byteArrayOf()

            if (bodyLength > 0) {
                body = ByteArray(bodyLength)
                var i = headLength + dateTimeLength
                var j = 0
                while (j < bodyLength) {
                    body[j] = listRawData[i]
                    i++
                    j++
                }
            } else
                body = byteArrayOf()
        }
        return
    }

    val dateTimeString: String
        get() = if (DT == null) "Unknown" else DT!!

    val decodedDataAsString: String
        get() = if (decodedData.size == 0) if (isNoDataEntry) "No data" else "" else decodedData.toString()

    fun hasData(): Boolean {
        return decodedData.size == 0 || isNoDataEntry || entryTypeName == "UnabsorbedInsulin"
    }

    val isNoDataEntry: Boolean
        get() = sizes[0] == 2 && sizes[1] == 5 && sizes[2] == 0

    fun getDecodedDataEntry(key: String?): Any? {
        return if (decodedData.containsKey(key)) decodedData[key] else null
    }

    fun hasDecodedDataEntry(key: String?): Boolean {
        return decodedData.containsKey(key)
    }

    fun showRaw(): Boolean {
        return entryTypeName == "EndResultTotals"
    }

    val headLength: Int
        get() = sizes[0]

    val dateTimeLength: Int
        get() = sizes[1]

    val bodyLength: Int
        get() = sizes[2]

    abstract fun toEntryString(): String

    override fun toString(): String {
        val sb = StringBuilder()
        // if (DT == null) {
        //     Log.e("", "DT is null. RawData=" + ByteUtil.getHex(rawData))
        // }
        sb.append(toStringStart)
        sb.append(", DT: " + if (DT == null) "null" else StringUtil.getStringInLength(DT, 19))
        sb.append(", length=")
        sb.append(headLength)
        sb.append(",")
        sb.append(dateTimeLength)
        sb.append(",")
        sb.append(bodyLength)
        sb.append("(")
        sb.append(headLength + dateTimeLength + bodyLength)
        sb.append(")")

        val hasData = hasData()
        if (hasData) {
            sb.append(", data=$decodedDataAsString")
        }
        if (hasData && !showRaw()) {
            sb.append("]")
            return sb.toString()
        }
        if (head.isNotEmpty()) {
            sb.append(", head=")
            sb.append(ByteUtil.shortHexString(head))
        }
        if (datetime.size != 0) {
            sb.append(", datetime=")
            sb.append(ByteUtil.shortHexString(datetime))
        }
        if (body.size != 0) {
            sb.append(", body=")
            sb.append(ByteUtil.shortHexString(body))
        }
        sb.append(", rawData=")
        sb.append(ByteUtil.shortHexString(rawData))
        sb.append("]")

        // sb.append(" DT: ");
        // sb.append(this.dateTime == null ? " - " : this.dateTime.toString("dd.MM.yyyy HH:mm:ss"));

        // sb.append(" Ext: ");
        return sb.toString()
    }

    abstract val opCode: Byte?
    abstract val toStringStart: String?

    fun getRawDataByIndex(index: Int): Byte {
        return rawData[index]
    }

    fun getRawDataByIndexInt(index: Int): Int {
        return rawData[index].toInt()
    }

    fun getUnsignedRawDataByIndex(index: Int): Int {
        return ByteUtil.convertUnsignedByteToInt(rawData[index])
    }

    fun addDecodedData(key: String, value: Any) {
        decodedData.put(key, value)
    }

    fun toShortString(): String {
        return if (head.size != 0) {
            "Unidentified record. "
        } else {
            "HistoryRecord: head=[" + ByteUtil.shortHexString(head) + "]"
        }
    }

    fun containsDecodedData(key: String?): Boolean {
        return decodedData.containsKey(key)
    } // if we extend to CGMS this need to be changed back
    // public abstract PumpHistoryEntryType getEntryType();
}