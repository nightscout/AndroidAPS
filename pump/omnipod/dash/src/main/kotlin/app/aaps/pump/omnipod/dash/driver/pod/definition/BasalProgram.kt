package app.aaps.pump.omnipod.dash.driver.pod.definition

import java.util.*

class BasalProgram(
    segments: List<Segment>
) {

    val segments: MutableList<Segment> = segments.toMutableList()
        get() = Collections.unmodifiableList(field)

    fun addSegment(segment: Segment) {
        segments.add(segment)
    }

    fun hasZeroUnitSegments() = segments.any { it.basalRateInHundredthUnitsPerHour == 0 }

    fun rateAt(date: Long): Double {
        val instance = Calendar.getInstance()
        instance.timeInMillis = date
        val hourOfDay = instance[Calendar.HOUR_OF_DAY]
        val minuteOfHour = instance[Calendar.MINUTE]
        val slotIndex = hourOfDay * 2 + minuteOfHour.div(30)
        val slot = segments.find { it.startSlotIndex <= slotIndex && slotIndex < it.endSlotIndex }
        return (slot?.basalRateInHundredthUnitsPerHour ?: 0).toDouble() / 100
    }

    class Segment(
        val startSlotIndex: Short,
        val endSlotIndex: Short,
        val basalRateInHundredthUnitsPerHour: Int
    ) {

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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Segment

            if (startSlotIndex != other.startSlotIndex) return false
            if (endSlotIndex != other.endSlotIndex) return false
            if (basalRateInHundredthUnitsPerHour != other.basalRateInHundredthUnitsPerHour) return false

            return true
        }

        override fun hashCode(): Int {
            var result: Int = startSlotIndex.toInt()
            result = 31 * result + endSlotIndex
            result = 31 * result + basalRateInHundredthUnitsPerHour
            return result
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BasalProgram

        return segments == other.segments
    }

    override fun hashCode(): Int {
        return segments.hashCode()
    }
}
