package app.aaps.pump.medtronic.data.dto

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.medtronic.util.MedtronicUtil
import com.google.gson.annotations.Expose
import org.joda.time.Instant
import java.util.Locale

/**
 * Created by geoff on 6/1/15.
 *
 *
 * There are three basal profiles stored on the pump. (722 only?) They are all parsed the same, the user just has 3 to
 * choose from: Standard, A, and B
 *
 *
 * The byte array is 48 times three byte entries long, plus a zero? If the profile is completely empty, it should have
 * one entry: [0,0,0x3F]. The first entry of [0,0,0] marks the end of the used entries.
 *
 *
 * Each entry is assumed to span from the specified start time to the start time of the next entry, or to midnight if
 * there are no more entries.
 *
 *
 * Individual entries are of the form [r,z,m] where r is the rate (in 0.025 U increments) z is zero (?) m is the start
 * time-of-day for the basal rate period (in 30 minute increments)
 */
class BasalProfile {

    private val aapsLogger: AAPSLogger

    @Expose
    lateinit var rawData: ByteArray // store as byte array to make transport (via parcel) easier
        private set

    private var listEntries: MutableList<BasalProfileEntry>? = null

    constructor(aapsLogger: AAPSLogger) {
        this.aapsLogger = aapsLogger
        init()
    }

    constructor(aapsLogger: AAPSLogger, data: ByteArray) {
        this.aapsLogger = aapsLogger
        setRawData(data)
    }

    fun init() {
        rawData = byteArrayOf(0, 0, 0x3f)
    }

    private fun setRawData(data: ByteArray): Boolean {
        var dataInternal: ByteArray = data

        // if we have just one entry through all day it looks like just length 1
        if (dataInternal.size == 1) {
            dataInternal = byteArrayOf(dataInternal[0], 0, 0)
        }
        if (dataInternal.size == MAX_RAW_DATA_SIZE) {
            rawData = dataInternal
        } else {
            val len = MAX_RAW_DATA_SIZE.coerceAtMost(data.size)
            rawData = ByteArray(MAX_RAW_DATA_SIZE)
            System.arraycopy(data, 0, rawData, 0, len)
        }
        return true
    }

    fun setRawDataFromHistory(data: ByteArray?): Boolean {
        if (data == null) {
            aapsLogger.error(LTag.PUMPCOMM, "setRawData: buffer is null!")
            return false
        }
        rawData = ByteArray(MAX_RAW_DATA_SIZE)
        var i = 0
        while (i < data.size - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte()) {
                rawData[i] = 0
                rawData[i + 1] = 0
                rawData[i + 2] = 0
            }
            rawData[i] = data[i + 1]
            rawData[i + 1] = data[i + 2]
            rawData[i + 2] = data[i]
            i += 3
        }
        return true
    }

    fun dumpBasalProfile() {
        aapsLogger.debug(LTag.PUMPCOMM, "Basal Profile entries:")
        val entries = getEntries()
        for (i in entries.indices) {
            val entry = entries[i]
            val startString = entry.startTime!!.toString("HH:mm")
            // this doesn't work
            aapsLogger.debug(
                LTag.PUMPCOMM, String.format(
                    Locale.ENGLISH, "Entry %d, rate=%.3f (%s), start=%s (0x%02X)", i + 1, entry.rate,
                    ByteUtil.getHex(entry.rate_raw), startString, entry.startTime_raw
                )
            )
        }
    }

    val basalProfileAsString: String
        get() {
            val sb = StringBuffer("Basal Profile entries:\n")
            val entries = getEntries()
            for (i in entries.indices) {
                val entry = entries[i]
                val startString = entry.startTime!!.toString("HH:mm")
                sb.append(String.format(Locale.ENGLISH, "Entry %d, rate=%.3f, start=%s\n", i + 1, entry.rate, startString))
            }
            return sb.toString()
        }

    fun basalProfileToStringError(): String {
        return "Basal Profile [rawData=" + ByteUtil.shortHexString(rawData) + "]"
    }

    fun basalProfileToString(): String {
        val sb = StringBuffer("Basal Profile [")
        val entries = getEntries()
        for (i in entries.indices) {
            val entry = entries[i]
            val startString = entry.startTime!!.toString("HH:mm")
            sb.append(String.format(Locale.ENGLISH, "%s=%.3f, ", startString, entry.rate))
        }
        sb.append("]")
        return sb.toString()
    }

    // TODO: this function must be expanded to include changes in which profile is in use.
    // and changes to the profiles themselves.
    fun getEntryForTime(whenever: Instant): BasalProfileEntry {
        var rval = BasalProfileEntry()
        val entries = getEntries()
        if (entries.isEmpty()) {
            aapsLogger.warn(
                LTag.PUMPCOMM, String.format(
                    Locale.ENGLISH, "getEntryForTime(%s): table is empty",
                    whenever.toDateTime().toLocalTime().toString("HH:mm")
                )
            )
            return rval
        }
        // Log.w(TAG,"Assuming first entry");
        rval = entries[0]
        if (entries.size == 1) {
            aapsLogger.debug(LTag.PUMPCOMM, "getEntryForTime: Only one entry in profile")
            return rval
        }
        val localMillis = whenever.toDateTime().toLocalTime().millisOfDay
        var done = false
        var i = 1
        while (!done) {
            val entry = entries[i]
            if (DEBUG_BASALPROFILE) {
                aapsLogger.debug(
                    LTag.PUMPCOMM, String.format(
                        Locale.ENGLISH, "Comparing 'now'=%s to entry 'start time'=%s", whenever.toDateTime().toLocalTime()
                            .toString("HH:mm"), entry.startTime!!.toString("HH:mm")
                    )
                )
            }
            if (localMillis >= entry.startTime!!.millisOfDay) {
                rval = entry
                if (DEBUG_BASALPROFILE) aapsLogger.debug(LTag.PUMPCOMM, "Accepted Entry")
            } else {
                // entry at i has later start time, keep older entry
                if (DEBUG_BASALPROFILE) aapsLogger.debug(LTag.PUMPCOMM, "Rejected Entry")
                done = true
            }
            i++
            if (i >= entries.size) {
                done = true
            }
        }
        if (DEBUG_BASALPROFILE) {
            aapsLogger.debug(
                LTag.PUMPCOMM, String.format(
                    Locale.ENGLISH, "getEntryForTime(%s): Returning entry: rate=%.3f (%s), start=%s (%d)", whenever
                        .toDateTime().toLocalTime().toString("HH:mm"), rval.rate, ByteUtil.getHex(rval.rate_raw),
                    rval.startTime!!.toString("HH:mm"), rval.startTime_raw
                )
            )
        }
        return rval
    }// readUnsignedByte(mRawData[i]);

    // an empty list
    fun getEntries(): List<BasalProfileEntry> {
        val entries: MutableList<BasalProfileEntry> = ArrayList()
        if (rawData[2] == 0x3f.toByte()) {
            aapsLogger.warn(LTag.PUMPCOMM, "Raw Data is empty.")
            return entries // an empty list
        }
        var r: Int
        var st: Int
        var i = 0
        while (i < rawData.size - 2) {
            if (rawData[i] == 0.toByte() && rawData[i + 1] == 0.toByte() && rawData[i + 2] == 0.toByte()) break
            if (rawData[i] == 0.toByte() && rawData[i + 1] == 0.toByte() && rawData[i + 2] == 0x3f.toByte()) break
            r = MedtronicUtil.makeUnsignedShort(rawData[i + 1].toInt(), rawData[i].toInt()) // readUnsignedByte(mRawData[i]);
            st = readUnsignedByte(rawData[i + 2])
            try {
                entries.add(BasalProfileEntry(aapsLogger, r, st))
            } catch (ex: Exception) {
                aapsLogger.error(LTag.PUMPCOMM, "Error decoding basal profile from bytes: " + ByteUtil.shortHexString(rawData))
                throw ex
            }
            i += 3
        }
        return entries
    }

    /**
     * This is used to prepare new profile
     *
     * @param entry
     */
    fun addEntry(entry: BasalProfileEntry) {
        if (listEntries == null) listEntries = ArrayList()
        listEntries!!.add(entry)
    }

    fun generateRawDataFromEntries() {
        val outData: MutableList<Byte> = ArrayList()
        for (profileEntry in listEntries!!) {
            //val strokes = MedtronicUtil.getBasalStrokes(profileEntry.rate, true)
            outData.add(profileEntry.rate_raw[0])
            outData.add(profileEntry.rate_raw[1])
            outData.add(profileEntry.startTime_raw)
        }
        setRawData(MedtronicUtil.createByteArray(outData))

        // return this.mRawData;
    }

    fun getProfilesByHour(pumpType: PumpType): DoubleArray {
        var entriesCopy: List<BasalProfileEntry>? = null
        try {
            entriesCopy = getEntries()
        } catch (ex: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "=============================================================================")
            aapsLogger.error(LTag.PUMPCOMM, "  Error generating entries. Ex.: $ex", ex)
            aapsLogger.error(LTag.PUMPCOMM, "  rawBasalValues: " + ByteUtil.shortHexString(rawData))
            aapsLogger.error(LTag.PUMPCOMM, "=============================================================================")
            //FabricUtil.createEvent("MedtronicBasalProfileGetByHourError", null);
        }

        val basalByHour = DoubleArray(24)

        if (entriesCopy == null || entriesCopy.isEmpty()) {
            for (i in 0..23) {
                basalByHour[i] = 0.0
            }
            return basalByHour
        }

        for (i in entriesCopy.indices) {
            val current = entriesCopy[i]
            var currentTime = if (current.startTime_raw % 2 == 0) current.startTime_raw.toInt() else current.startTime_raw - 1
            currentTime = currentTime * 30 / 60
            var lastHour: Int =
                if (i + 1 == entriesCopy.size) {
                    24
                } else {
                    val basalProfileEntry = entriesCopy[i + 1]
                    val rawTime = if (basalProfileEntry.startTime_raw % 2 == 0) basalProfileEntry.startTime_raw.toInt() else basalProfileEntry.startTime_raw - 1
                    rawTime * 30 / 60
                }

            // System.out.println("Current time: " + currentTime + " Next Time: " + lastHour);
            for (j in currentTime until lastHour) {
                // if (pumpType == null)
                //     basalByHour[j] = current.rate
                // else
                basalByHour[j] = pumpType.determineCorrectBasalSize(current.rate)
            }
        }
        return basalByHour
    }

    override fun toString(): String {
        return basalProfileToString()
    }

    fun verify(pumpType: PumpType): Boolean {
        try {
            getEntries()
        } catch (_: Exception) {
            return false
        }
        val profilesByHour = getProfilesByHour(pumpType)
        for (aDouble in profilesByHour) {
            if (aDouble > 35.0) return false
        }
        return true
    }

    companion object {

        const val MAX_RAW_DATA_SIZE = 48 * 3 + 1
        private const val DEBUG_BASALPROFILE = false

        private fun readUnsignedByte(b: Byte): Int {
            return if (b < 0) b + 256 else b.toInt()
        }

        fun getProfilesByHourToString(data: DoubleArray): String {
            val stringBuilder = StringBuilder()
            for (value in data) {
                stringBuilder.append(String.format("%.3f", value))
                stringBuilder.append(" ")
            }
            return stringBuilder.toString()
        }

        fun isBasalProfileByHourUndefined(basalByHour: DoubleArray): Boolean {
            for (i in 0..23) {
                if (basalByHour[i] > 0.0) {
                    return false
                }
            }
            return true
        }
    }
}