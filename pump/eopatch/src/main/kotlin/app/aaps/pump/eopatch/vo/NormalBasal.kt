package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.code.BasalStatus
import app.aaps.pump.eopatch.core.util.FloatAdjusters
import java.math.BigDecimal
import java.util.Calendar

class NormalBasal : SegmentsEntity<BasalSegment>() {

    var status: BasalStatus = BasalStatus.SELECTED
        @Synchronized
        set(status) {
            field = status
            if (status == BasalStatus.STOPPED) {
                this.segmentIndex = SEGMENT_INDEX_DEFAULT
            }
        }

    private var segmentIndex = SEGMENT_INDEX_DEFAULT

    /**
     * The half-hour doses the patch accepted for this basal, or null when they were not sent yet. A patch keeps
     * the program it was given, so after an update that changed the rounding it still runs the old one until it
     * gets this basal again (#2454).
     */
    var sentDoses: FloatArray? = null

    /**
     * The dose of each half hour, as the patch gives it. The patch gives 0.05 U steps per half hour.
     *
     * The rate is rounded to the nearest 0.05 U/h first, and never to zero: 0.42 U/h is given as 0.40, not 0.45
     * as before (#2454). A rate with an odd number of 0.05 steps alternates within its block, starting with the
     * larger half: 0.45 U/h is 0.25 + 0.20, and 0.05 U/h is 0.05 + 0.00, which is the right amount each hour.
     */
    val doseUnitPerSegmentArray: FloatArray
        get() {
            val doseArray = FloatArray(AppConstant.SEGMENT_COUNT_MAX)
            for (segment in list) {
                val halfHourDose = deliveredRate(segment.doseUnitPerHour) / 2
                for (index in segment.startIndex until segment.endIndex)
                    doseArray[index] =
                        if ((index - segment.startIndex) % 2 == 0) FloatAdjusters.CEIL2_BASAL_RATE(halfHourDose)
                        else FloatAdjusters.FLOOR2_BASAL_RATE(halfHourDose)
            }
            return doseArray
        }

    /** True when the patch runs this basal as it is rounded now: the doses it accepted are the ones it would get now. */
    val isSentWithCurrentRounding: Boolean
        get() = sentDoses?.contentEquals(doseUnitPerSegmentArray) == true

    /**
     * The insulin the patch really gives in each hour of the day, from midnight: the doses of its two half hours,
     * as it accepted them, or as it will get them when they were not sent yet. Hour values only, like the running
     * profile in AAPS. For a block of whole hours this is its rate rounded to the nearest 0.05 U/h.
     */
    val deliveredDoseUnitPerHour: List<Double>
        get() {
            val doseArray = sentDoses ?: doseUnitPerSegmentArray
            // Summed as decimals: 0.25f + 0.2f as a float is 0.44999998.
            return (0 until HOURS_PER_DAY).map { hour -> (BigDecimal(doseArray[2 * hour].toString()) + BigDecimal(doseArray[2 * hour + 1].toString())).toDouble() }
        }

    /** The rate the patch really gives in this hour, see [deliveredDoseUnitPerHour]. */
    val currentDeliveredDoseUnitPerHour: Double
        get() = deliveredDoseUnitPerHour[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)]

    private val currentSegmentIndex: Int
        get() {
            val cal = Calendar.getInstance()
            var idx = cal.get(Calendar.HOUR_OF_DAY) * 2
            if (cal.get(Calendar.MINUTE) >= 30) {
                idx += 1
            }
            return idx
        }

    init {
        initObject()
    }

    fun initObject() {
        status = BasalStatus.SELECTED
        list.add(BasalSegment.create(AppConstant.BASAL_RATE_PER_HOUR_MIN))
    }

    @Synchronized
    fun updateNormalBasalIndex(): Boolean {
        val currentSegmentIndex = currentSegmentIndex
        if (segmentIndex != SEGMENT_INDEX_DEFAULT) {
            if (segmentIndex != currentSegmentIndex) {
                segmentIndex = currentSegmentIndex
                return true
            }
        } else {
            segmentIndex = currentSegmentIndex
            return true
        }
        return false
    }

    override fun toString(): String {
        return "NormalBasal(status=$status, segmentIndex=$segmentIndex, list=$list, sentDoses=${sentDoses?.contentToString()})"
    }

    companion object {

        private const val SEGMENT_INDEX_DEFAULT = -1
        private const val HOURS_PER_DAY = 24

        fun create(firstSegmentDoseUnitPerHour: Float): NormalBasal {
            val b = NormalBasal()
            b.status = BasalStatus.SELECTED
            b.list[0].doseUnitPerHour = firstSegmentDoseUnitPerHour
            return b
        }

        /**
         * The rate rounded to the nearest 0.05 U/h. A rate above zero stays at least 0.05: most pumps cannot
         * give zero basal, and rounding must not stop the insulin.
         */
        fun deliveredRate(doseUnitPerHour: Float): Float =
            if (doseUnitPerHour <= 0f) 0f
            else maxOf(FloatAdjusters.ROUND2_BASAL_RATE(doseUnitPerHour), AppConstant.BASAL_RATE_PER_HOUR_MIN)
    }
}
