package info.nightscout.androidaps.plugins.pump.eopatch.vo

import android.util.MutableFloat
import info.nightscout.androidaps.plugins.pump.eopatch.AppConstant
import info.nightscout.androidaps.plugins.pump.eopatch.core.util.FloatAdjusters
import info.nightscout.androidaps.plugins.pump.eopatch.code.BasalStatus
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction

class NormalBasal : SegmentsEntity<BasalSegment>() {
    var status: BasalStatus = BasalStatus.SELECTED
        @Synchronized
        set(status) {
            field = status
            if (status == BasalStatus.STOPPED) {
                this.segmentIndex = SEGMENT_INDEX_DEFAULT
            }
        }

    var segmentIndex = SEGMENT_INDEX_DEFAULT

    val maxDoseUnitPerHour: Float
        get() {
            val max = list.stream().map({ it.doseUnitPerHour }).mapToDouble({ it.toDouble() }).max().orElse(0.0).toFloat()
            return FloatAdjusters.ROUND2_INSULIN.apply(max)
        }

    val doseUnitPerSegmentArray: FloatArray
        get() {
            val doseArray = FloatArray(AppConstant.SEGMENT_COUNT_MAX)

            eachSegmentItem(BiFunction { index, segment ->
                val dose = segment.doseUnitPerHour / 2
                if (index % 2 == 0) {
                    doseArray[index] = FloatAdjusters.CEIL2_BASAL_RATE.apply(dose)
                } else {
                    doseArray[index] = FloatAdjusters.FLOOR2_BASAL_RATE.apply(dose)
                }
                true
            })
            return doseArray
        }

    val doseUnitPerSegmentArrayForGraph: FloatArray
        get() {
            val doseArray = FloatArray(AppConstant.SEGMENT_COUNT_MAX)

            eachSegmentItem(BiFunction { index, segment ->
                doseArray[index] = FloatAdjusters.CEIL2_BASAL_RATE.apply(segment.doseUnitPerHour)
                true
            })
            return doseArray
        }

    val doseUnitPerDay: Float
        get() {
            val total = MutableFloat(0f)
            eachSegmentItem(BiFunction { index, segment ->
                val dose = segment.doseUnitPerHour / 2
                if (index % 2 == 0) {
                    total.value += FloatAdjusters.CEIL2_BASAL_RATE.apply(dose)
                } else {
                    total.value += FloatAdjusters.FLOOR2_BASAL_RATE.apply(dose)
                }
                true
            })
            return total.value
        }

    val currentSegmentDoseUnitPerHour: Float
        get() = FloatAdjusters.ROUND2_INSULIN.apply(getSegmentDoseUnitPerHourByIndex(currentSegmentIndex))

    val firstSegmentDoseUnitPerHour: Float
        get() = FloatAdjusters.ROUND2_INSULIN.apply(getSegmentDoseUnitPerHourByIndex(0))

    val currentSegmentIndex: Int
        get() {
            val cal = Calendar.getInstance()
            var idx = cal.get(Calendar.HOUR_OF_DAY) * 2
            if (cal.get(Calendar.MINUTE) >= 30) {
                idx += 1
            }
            return idx
        }

    val isDoseUChanged: Boolean
        get() {
            val currentSegmentIndex = currentSegmentIndex
            if (segmentIndex != SEGMENT_INDEX_DEFAULT && segmentIndex != currentSegmentIndex) {
                val beforeDoesU = getSegmentDoseUnitPerHourByIndex(segmentIndex)
                val currentDoseU = getSegmentDoseUnitPerHourByIndex(currentSegmentIndex)
                if (beforeDoesU != currentDoseU) {
                    return true
                }
            }
            return false
        }

    val startTime: Long
        get() = getStartTime(currentSegmentIndex)

    init {
        initObject()
    }

    fun initObject() {
        status = BasalStatus.SELECTED
        list.add(BasalSegment.create(AppConstant.BASAL_RATE_PER_HOUR_MIN))
    }

    fun getSegmentDoseUnitPerHour(time: Long): Float {
        return FloatAdjusters.ROUND2_INSULIN.apply(getSegmentDoseUnitPerHourByIndex(getSegmentIndex(time)))
    }

    fun getSegmentDoseUnitPerHourByIndex(idx: Int): Float {
        val defaultValue = 0f
        for (seg in list) {
            val startIndex = seg.startIndex
            val endIndex = seg.endIndex
            if (startIndex <= idx && idx < endIndex) {
                return seg.doseUnitPerHour
            }
        }
        return defaultValue
    }

    private fun getSegmentIndex(time: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        var idx = cal.get(Calendar.HOUR_OF_DAY) * 2
        if (cal.get(Calendar.MINUTE) >= 30) {
            idx += 1
        }
        return idx
    }

    fun getMaxBasal(durationMinutes: Long): Float {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        var hours = calendar.get(Calendar.HOUR_OF_DAY)
        var minutes = calendar.get(Calendar.MINUTE)

        var startIndex = hours * 2 + minutes / 30
        startIndex = startIndex % AppConstant.SEGMENT_COUNT_MAX

        hours = (hours + durationMinutes / 60).toInt()
        minutes = (minutes + durationMinutes % 60).toInt()

        var endIndex = hours * 2 + minutes / 30
        endIndex = endIndex % AppConstant.SEGMENT_COUNT_MAX

        val segments = doseUnitPerSegmentArrayForGraph
        var maxBasal = segments[startIndex]
        var i = startIndex
        while (i != endIndex + 1) {
            if (i >= AppConstant.SEGMENT_COUNT_MAX) {
                i = i % AppConstant.SEGMENT_COUNT_MAX
            }
            maxBasal = Math.max(maxBasal, segments[i])
            i++
        }
        return maxBasal
    }

    fun isIndexChanged(): Boolean {
        return (segmentIndex != SEGMENT_INDEX_DEFAULT && segmentIndex != currentSegmentIndex)
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

    fun getStartTime(segmentIndex: Int): Long {
        val curIndexTime: Long
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        curIndexTime = calendar.timeInMillis + TimeUnit.MINUTES.toMillis((segmentIndex * 30).toLong())
        return curIndexTime
    }

    override fun toString(): String {
        return "NormalBasal(status=$status, segmentIndex=$segmentIndex, list=$list)"
    }

    companion object {

        private val HALF_HOUR = 0.5f
        private val SEGMENT_INDEX_DEFAULT = -1

        fun create(firstSegmentDoseUnitPerHour: Float): NormalBasal {
            val b = NormalBasal()
            b.status = BasalStatus.SELECTED
            b.list[0].doseUnitPerHour = firstSegmentDoseUnitPerHour
            return b
        }
    }
}
