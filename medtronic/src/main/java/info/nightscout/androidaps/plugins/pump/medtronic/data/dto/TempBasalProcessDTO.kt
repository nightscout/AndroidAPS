package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry

class TempBasalProcessDTO constructor(var itemOne: PumpHistoryEntry,
                                      var itemTwo: PumpHistoryEntry? = null,
                                      var processOperation: Operation = Operation.None,
                                      var aapsLogger: AAPSLogger) {

    var cancelPresent: Boolean = false

    val atechDateTime: Long
        get() = itemOne.atechDateTime

    val pumpId: Long
        get() = itemOne.pumpId

    val duration: Int
        get() = if (itemTwo == null) {
            val tbr = itemOne.getDecodedDataEntry("Object") as? TempBasalPair
            if (tbr != null)
                tbr.durationMinutes
            else {
                aapsLogger.error("Couldn't find TempBasalPair in entry: $itemOne")
                0
            }
        } else {
            DateTimeUtil.getATechDateDiferenceAsMinutes(itemOne.atechDateTime, itemTwo!!.atechDateTime)
        }

    enum class Operation {
        None, Add, Edit
    }
}