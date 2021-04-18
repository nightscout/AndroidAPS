package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry

class TempBasalProcessDTO {
    @JvmField var itemOne: PumpHistoryEntry? = null
    @JvmField var itemTwo: PumpHistoryEntry? = null
    @JvmField var processOperation = Operation.None
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