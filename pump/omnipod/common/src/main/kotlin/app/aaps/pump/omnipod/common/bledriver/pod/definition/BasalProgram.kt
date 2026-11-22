package app.aaps.pump.omnipod.common.bledriver.pod.definition

import com.google.gson.annotations.SerializedName
import java.util.Collections
import java.util.TimeZone

class BasalProgram(
    segments: List<Segment>
) {

    // Keeps the JSON key "segments" so saved Pod state keeps deserializing across this field's rename.
    // Alternate can be removed once all users have upgraded from the O5 beta builds.
    @SerializedName(value = "segments", alternate = ["mutableSegmentsOrNull"])
    private var mutableSegmentsOrNull: MutableList<Segment>? = segments.toMutableList()
    private val mutableSegments: MutableList<Segment>
        get() = mutableSegmentsOrNull ?: mutableListOf<Segment>().also { mutableSegmentsOrNull = it }

    val segments: MutableList<Segment> get() = Collections.unmodifiableList(mutableSegments)

    fun addSegment(segment: Segment) {
        mutableSegments.add(segment)
    }

    fun hasZeroUnitSegments() = segments.any { it.basalRateInHundredthUnitsPerHour == 0 }

    /** [podOffset] in milliseconds; falls back to the device's current time zone offset if unknown. */
    fun rateAt(date: Long, podOffset: Int?): Double {
        val offset = podOffset ?: TimeZone.getDefault().getOffset(date)
        val localMillis = Math.floorMod(date + offset, 86400000L)
        return rateInHundredthsAtSlot((localMillis / 1800000L).toInt()) / 100.0
    }

    /**
     * The insulin of each hour of the day from midnight, in U/h: the mean rate of its two half-hour slots. Hour values
     * only, like the running profile in AAPS (#2454). A profile with hourly rates gives back its rates as the pod got them.
     */
    fun hourlyRates(): List<Double> =
        (0 until 24).map { hour -> (rateInHundredthsAtSlot(2 * hour) + rateInHundredthsAtSlot(2 * hour + 1)) / 200.0 }

    private fun rateInHundredthsAtSlot(slotIndex: Int): Int =
        segments.find { it.startSlotIndex <= slotIndex && slotIndex < it.endSlotIndex }?.basalRateInHundredthUnitsPerHour ?: 0

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
