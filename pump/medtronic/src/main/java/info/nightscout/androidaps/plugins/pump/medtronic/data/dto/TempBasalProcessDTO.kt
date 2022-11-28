package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntryType
import info.nightscout.core.utils.DateTimeUtil
import info.nightscout.rx.logging.AAPSLogger

class TempBasalProcessDTO constructor(var itemOne: PumpHistoryEntry,
                                      var aapsLogger: AAPSLogger,
                                      var objectType: ObjectType = ObjectType.TemporaryBasal) {

    var itemTwo: PumpHistoryEntry? = null
        set(value) {
            field = value
            if (objectType == ObjectType.TemporaryBasal) {
                if (value!=null) {
                    if (value.entryType == PumpHistoryEntryType.TempBasalCombined) {
                        itemTwoTbr = value.getDecodedDataEntry("Object") as TempBasalPair
                    } else {
                        itemTwoRewind = value
                    }
                } else {
                    itemTwoTbr = null
                }
            }
        }

    var itemOneTbr: TempBasalPair? = null
    var itemTwoTbr: TempBasalPair? = null
    var itemTwoRewind: PumpHistoryEntry? = null

    val atechDateTime: Long
        get() = itemOne.atechDateTime

    val pumpId: Long
        get() = itemOne.pumpId

    val durationAsSeconds: Int
        get() {
            //aapsLogger.debug(LTag.PUMP, "durationAsSeconds: [objectType=$objectType]")
            if (objectType == ObjectType.TemporaryBasal) {
                if (itemTwo == null) {
                    if (itemOneTbr != null) {
                        //aapsLogger.debug("TemporaryBasalPair - itemOneSingle: $itemOneTbr")
                        return itemOneTbr!!.durationMinutes * 60
                    } else {
                        //aapsLogger.error("Couldn't find TempBasalPair in entry: $itemOne")
                        return 0
                    }
                } else if (itemTwoRewind!=null) {
                    val secondsDiff = DateTimeUtil.getATechDateDifferenceAsSeconds(itemOne.atechDateTime, DateTimeUtil.getATDWithAddedSeconds(itemTwo!!.atechDateTime, -2))
                    return secondsDiff
                } else {
                    //aapsLogger.debug(LTag.PUMP, "Found 2 items for duration: itemOne=$itemOne, itemTwo=$itemTwo")
                    val secondsDiff = DateTimeUtil.getATechDateDifferenceAsSeconds(itemOne.atechDateTime, itemTwo!!.atechDateTime)
                    //aapsLogger.debug(LTag.PUMP, "Difference in seconds: $secondsDiff")
                    return secondsDiff
                }
            } else {
                //aapsLogger.debug(LTag.PUMP, "Found 2 items for duration (in SuspendMode): itemOne=$itemOne, itemTwo=$itemTwo")
                val secondsDiff = DateTimeUtil.getATechDateDifferenceAsSeconds(itemOne.atechDateTime, itemTwo!!.atechDateTime)
                //aapsLogger.debug(LTag.PUMP, "Difference in seconds: $secondsDiff")
                return secondsDiff
            }
        }

    init {
        if (objectType == ObjectType.TemporaryBasal) {
            itemOneTbr = itemOne.getDecodedDataEntry("Object") as TempBasalPair
        }
    }

    fun toTreatmentString(): String {
        val stringBuilder = StringBuilder()

        stringBuilder.append(itemOne.DT)

        if (itemTwo!=null) {
            stringBuilder.append(" - ")
            stringBuilder.append(itemTwo?.DT)
        }

        stringBuilder.append("  " + durationAsSeconds + " s (" + durationAsSeconds/60 + ")")

        if (itemTwoTbr!=null) {
            stringBuilder.append("  " + itemOneTbr?.insulinRate + " / " + itemTwoTbr?.insulinRate)
        } else {
            stringBuilder.append("  " + itemOneTbr?.insulinRate)
        }

        return stringBuilder.toString()
    }

    override fun toString(): String {
        return "ItemOne: $itemOne, ItemTwo: $itemTwo, Duration: $durationAsSeconds, ObjectType: $objectType"
    }

    enum class ObjectType {
        TemporaryBasal,
        Suspend,
    }
}