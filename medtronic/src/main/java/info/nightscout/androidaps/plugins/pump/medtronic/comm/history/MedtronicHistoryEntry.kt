package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

import android.util.Log
import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import java.util.*

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
abstract class MedtronicHistoryEntry : MedtronicHistoryEntryInterface {

    @JvmField var rawData: List<Byte>? = null
    @JvmField protected var sizes = IntArray(3)
    var head: ByteArray? = null
    var datetime: ByteArray? = null
    var body: ByteArray? = null

    // protected LocalDateTime dateTime;
    @JvmField var id: Long = 0

    @JvmField @Expose
    var DT: String? = null

    @JvmField @Expose
    var atechDateTime: Long? = null

    @Expose
    protected var decodedData: MutableMap<String, Any?>? = null
    var phoneDateTime // time on phone
        : Long = 0

    /**
     * Pump id that will be used with AAPS object (time * 1000 + historyType (max is FF = 255)
     */
    protected open var pumpId: Long? = null

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
        get() = field //= linkedObject
        set(value) {
            linked = true
            field = value
        }

    // fun setLinkedObject(linkedObject: Any?) {
    //     linked = true
    //     this.linkedObject = linkedObject
    // }

    override fun setData(listRawData: List<Byte>?, doNotProcess: Boolean) {
        rawData = listRawData

        // System.out.println("Head: " + sizes[0] + ", dates: " + sizes[1] +
        // ", body=" + sizes[2]);
        if (!doNotProcess) {
            head = ByteArray(headLength - 1)
            for (i in 1 until headLength) {
                head!![i - 1] = listRawData!![i]!!
            }
            if (dateTimeLength > 0) {
                datetime = ByteArray(dateTimeLength)
                var i = headLength
                var j = 0
                while (j < dateTimeLength) {
                    datetime!![j] = listRawData!![i]!!
                    i++
                    j++
                }
            }
            if (bodyLength > 0) {
                body = ByteArray(bodyLength)
                var i = headLength + dateTimeLength
                var j = 0
                while (j < bodyLength) {
                    body!![j] = listRawData!![i]!!
                    i++
                    j++
                }
            }
        }
        return
    }

    val dateTimeString: String
        get() = if (DT == null) "Unknown" else DT!!

    val decodedDataAsString: String
        get() = if (decodedData == null) if (isNoDataEntry) "No data" else "" else decodedData.toString()

    fun hasData(): Boolean {
        return decodedData != null || isNoDataEntry || entryTypeName == "UnabsorbedInsulin"
    }

    val isNoDataEntry: Boolean
        get() = sizes[0] == 2 && sizes[1] == 5 && sizes[2] == 0

    // fun getDecodedData(): Map<String, Any?>? {
    //     return decodedData
    // }

    fun getDecodedDataEntry(key: String?): Any? {
        return if (decodedData != null) decodedData!![key] else null
    }

    fun hasDecodedDataEntry(key: String?): Boolean {
        return decodedData!!.containsKey(key)
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

    override fun toString(): String {
        val sb = StringBuilder()
        if (DT == null) {
            Log.e("", "DT is null. RawData=" + ByteUtil.getHex(rawData))
        }
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
        if (head != null) {
            sb.append(", head=")
            sb.append(ByteUtil.shortHexString(head))
        }
        if (datetime != null) {
            sb.append(", datetime=")
            sb.append(ByteUtil.shortHexString(datetime))
        }
        if (body != null) {
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
        return rawData!![index]
    }

    fun getRawDataByIndexInt(index: Int): Int {
        return rawData!![index].toInt()
    }

    fun getUnsignedRawDataByIndex(index: Int): Int {
        return ByteUtil.convertUnsignedByteToInt(rawData!![index])
    }

    fun setAtechDateTime(dt: Long) {
        atechDateTime = dt
        DT = DateTimeUtil.toString(atechDateTime!!)
    }

    fun addDecodedData(key: String, value: Any?) {
        if (decodedData == null) decodedData = HashMap()
        decodedData!![key] = value
    }

    fun toShortString(): String {
        return if (head == null) {
            "Unidentified record. "
        } else {
            "HistoryRecord: head=[" + ByteUtil.shortHexString(head) + "]"
        }
    }

    fun containsDecodedData(key: String?): Boolean {
        return if (decodedData == null) false else decodedData!!.containsKey(key)
    } // if we extend to CGMS this need to be changed back
    // public abstract PumpHistoryEntryType getEntryType();
}