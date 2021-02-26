package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition

import java.util.*

class BasalProgram(
    segments: List<Segment>
) {

    val segments: MutableList<Segment> = segments.toMutableList()
        get() = Collections.unmodifiableList(field) // TODO Adrian: moved method here

    fun addSegment(segment: Segment) {
        segments.add(segment)
    }

    fun hasZeroUnitSegments() = segments.any { it.basalRateInHundredthUnitsPerHour == 0 }

    fun isZeroBasal() = segments.sumBy(Segment::basalRateInHundredthUnitsPerHour) == 0

    fun rateAt(date: Date): Double = 0.0 // TODO

    class Segment(val startSlotIndex: Short, val endSlotIndex: Short, val basalRateInHundredthUnitsPerHour: Int) {

        fun getPulsesPerHour(): Short {
            return (basalRateInHundredthUnitsPerHour * PULSES_PER_UNIT / 100).toShort()
        }

        fun getNumberOfSlots(): Short {
            return (endSlotIndex - startSlotIndex).toShort()
        }

        override fun toString(): String {
            return "Segment{" +
                "startSlotIndex=" + startSlotIndex +
                ", endSlotIndex=" + endSlotIndex +
                ", basalRateInHundredthUnitsPerHour=" + basalRateInHundredthUnitsPerHour +
                '}'
        }

        companion object {

            private const val PULSES_PER_UNIT: Byte = 20
        }
    }

    override fun toString(): String {
        return "BasalProgram{" +
            "segments=" + segments +
            '}'
    }
}