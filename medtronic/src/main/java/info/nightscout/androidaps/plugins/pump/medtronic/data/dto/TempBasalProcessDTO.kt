package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry

class TempBasalProcessDTO {
    var itemOne: PumpHistoryEntry? = null
    var itemTwo: PumpHistoryEntry? = null
    var processOperation = Operation.None
    var cancelPresent: Boolean = false

    val atechDateTime: Long
        get() = if (itemOne==null) 0L else itemOne!!.atechDateTime!!

    val pumpId: Long
        get() = if (itemOne==null) 0L else itemOne!!.pumpId!!

    val duration: Int
        get() = if (itemTwo == null) {
            val tbr = itemOne!!.getDecodedDataEntry("Object") as TempBasalPair?
            tbr!!.durationMinutes
        } else {
            DateTimeUtil.getATechDateDiferenceAsMinutes(itemOne!!.atechDateTime, itemTwo!!.atechDateTime)
        }

    enum class Operation {
        None, Add, Edit
    }
}