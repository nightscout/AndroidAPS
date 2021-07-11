package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.MedtronicHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BolusDTO
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import java.util.*

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
class PumpHistoryEntry : MedtronicHistoryEntry() {

    @Expose
    var entryType: PumpHistoryEntryType = PumpHistoryEntryType.None
        private set

    override var opCode: Byte? = null
        // this is set only when we have unknown entry...
        get() = if (field == null) entryType.code else field

    var offset = 0
    var displayableValue = ""

    fun setEntryType(medtronicDeviceType: MedtronicDeviceType, entryType: PumpHistoryEntryType, opCode: Byte? = null) {
        this.entryType = entryType
        sizes[0] = entryType.getHeadLength(medtronicDeviceType)
        sizes[1] = entryType.dateLength
        sizes[2] = entryType.getBodyLength(medtronicDeviceType)
        if (isEntryTypeSet() && atechDateTime != 0L) pumpId = generatePumpId()
        this.opCode = opCode
    }

    override fun generatePumpId(): Long {
        return entryType.code + atechDateTime * 1000L
    }

    override fun isEntryTypeSet(): Boolean {
        return this.entryType != PumpHistoryEntryType.None
    }

    override val toStringStart: String
        get() = ("PumpHistoryEntry [type=" + StringUtil.getStringInLength(entryType.name, 20) + " ["
            + StringUtil.getStringInLength("" + opCode, 3) + ", 0x"
            + ByteUtil.shortHexString(opCode!!) + "]")

    override val entryTypeName: String
        get() = entryType.name

    override val dateLength: Int
        get() = entryType.dateLength

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PumpHistoryEntry) return false
        val that = other //as PumpHistoryEntry
        return this.pumpId == that.pumpId
        // return entryType == that.entryType &&  //
        //     atechDateTime === that.atechDateTime // && //
        // Objects.equals(this.decodedData, that.decodedData);
    }

    override fun hashCode(): Int {
        return Objects.hash(entryType, opCode, offset)
    }

    // public boolean isAfter(LocalDateTime dateTimeIn) {
    // // LOG.debug("Entry: " + this.dateTime);
    // // LOG.debug("Datetime: " + dateTimeIn);
    // // LOG.debug("Item after: " + this.dateTime.isAfter(dateTimeIn));
    // return this.dateTime.isAfter(dateTimeIn);
    // }
    fun isAfter(atechDateTime: Long): Boolean {
        if (this.atechDateTime == 0L) {
            // Log.e("PumpHistoryEntry", "Date is null. Show object: " + toString())
            return false // FIXME shouldn't happen
        }
        return atechDateTime < this.atechDateTime
    }

    class Comparator : java.util.Comparator<PumpHistoryEntry> {

        override fun compare(o1: PumpHistoryEntry, o2: PumpHistoryEntry): Int {
            val data = (o2.atechDateTime - o1.atechDateTime).toInt()
            return if (data != 0) data else o2.entryType.code - o1.entryType.code
        }
    }

    override var pumpId: Long = 0L
        get() {
            if (field == 0L) {
                field = generatePumpId()
            }
            return field
        }

    fun hasBolusChanged(entry: PumpHistoryEntry): Boolean {
        if (entryType == PumpHistoryEntryType.Bolus) {
            val thisOne: BolusDTO = this.decodedData["Object"] as BolusDTO

            if (entry.entryType == PumpHistoryEntryType.Bolus) {
                val otherOne: BolusDTO = entry.decodedData["Object"] as BolusDTO
                return (thisOne.value.equals(otherOne.value))
            } else
                return false
        }

        return false
    }
}