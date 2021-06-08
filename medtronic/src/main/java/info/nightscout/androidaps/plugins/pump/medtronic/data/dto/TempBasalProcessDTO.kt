package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry

class TempBasalProcessDTO constructor(var itemOne: PumpHistoryEntry,
                                      var processOperation: Operation = Operation.None,
                                      var aapsLogger: AAPSLogger) {

    var itemTwo: PumpHistoryEntry? = null
        set(value) {
            field = value
            itemTwoTbr = value!!.getDecodedDataEntry("Object") as TempBasalPair
        }

    var itemOneTbr: TempBasalPair? = null
    var itemTwoTbr: TempBasalPair? = null

    var cancelPresent: Boolean = false

    val atechDateTime: Long
        get() = itemOne.atechDateTime

    val pumpId: Long
        get() = itemOne.pumpId

    val duration: Int
        get() = if (itemTwo == null) {
            if (itemOneTbr != null) {
                aapsLogger.debug("TemporaryBasalPair: $itemOneTbr")
                itemOneTbr!!.durationMinutes
            } else {
                aapsLogger.error("Couldn't find TempBasalPair in entry: $itemOne")
                0
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "Found 2 items for duration: itemOne=$itemOne, itemTwo=$itemTwo")
            val minuteDiff = DateTimeUtil.getATechDateDiferenceAsMinutes(itemOne.atechDateTime, itemTwo!!.atechDateTime)
            aapsLogger.debug(LTag.PUMP, "Difference in minutes: $minuteDiff")
            minuteDiff
        }

    init {
        itemOneTbr = itemOne.getDecodedDataEntry("Object") as TempBasalPair
    }

    override fun toString(): String {
        return "ItemOne: $itemOne, ItemTwo: $itemTwo, Duration: $duration, Operation: $processOperation"
    }

    enum class Operation {
        None, Add, Edit
    }
}