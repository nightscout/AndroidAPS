package info.nightscout.androidaps.data

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.interfaces.Interval
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.concurrent.TimeUnit

class TempTargetTest(
    var data: TemporaryTarget = TemporaryTarget(
        timestamp = 0,
        utcOffset = 0,
        reason = TemporaryTarget.Reason.CUSTOM,
        highTarget = 0.0,
        lowTarget = 0.0,
        duration = 0
    )
) : Interval {

    fun date(timestamp: Long): TempTargetTest {
        data.timestamp = timestamp
        return this
    }

    fun duration(duration: Long): TempTargetTest {
        data.duration = T.mins(duration).msecs()
        return this
    }

    fun low(low: Double): TempTargetTest {
        data.lowTarget = low
        return this
    }

    fun high(high: Double): TempTargetTest {
        data.highTarget = high
        return this
    }

    fun target(): Double {
        return (data.lowTarget + data.highTarget) / 2
    }

    // -------- Interval interface ---------

    private var cuttedEnd: Long? = null

    override fun durationInMsec(): Long = data.duration
    override fun start(): Long = data.timestamp
    override fun originalEnd(): Long = data.end // planned end time at time of creation
    override fun end(): Long = cuttedEnd ?: originalEnd() // end time after cut
    override fun cutEndTo(end: Long) {
        cuttedEnd = end
    }

    override fun match(time: Long): Boolean = start() <= time && end() >= time
    override fun before(time: Long): Boolean = end() < time
    override fun after(time: Long): Boolean = start() > time
    override fun isInProgress(): Boolean = match(System.currentTimeMillis())
    override fun isEndingEvent(): Boolean = data.duration == 0L
    override fun isValid(): Boolean = true

    // -------- Interval interface end ---------

    fun lowValueToUnitsToString(units: String): String =
        if (units == Constants.MGDL) DecimalFormatter.to0Decimal(data.lowTarget)
        else DecimalFormatter.to1Decimal(data.lowTarget * Constants.MGDL_TO_MMOLL)

    fun highValueToUnitsToString(units: String): String =
        if (units == Constants.MGDL) DecimalFormatter.to0Decimal(data.highTarget)
        else DecimalFormatter.to1Decimal(data.highTarget * Constants.MGDL_TO_MMOLL)

    override fun toString(): String = data.toString()

    fun friendlyDescription(units: String, resourceHelper: ResourceHelper): String =
        Profile.toTargetRangeString(data.lowTarget, data.highTarget, Constants.MGDL, units) +
            units +
            "@" + resourceHelper.gs(R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(data.duration)) + "(" + data.reason.text + ")"
}