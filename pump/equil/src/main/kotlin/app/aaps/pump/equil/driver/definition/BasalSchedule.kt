package app.aaps.pump.equil.driver.definition

import app.aaps.core.interfaces.profile.Profile
import org.joda.time.Duration
import java.util.Objects

class BasalSchedule(private val entries: List<BasalScheduleEntry>) {

    init {
        require(entries.isNotEmpty()) { "Entries can not be empty" }
        require(entries[0].startTime.isEqual(Duration.ZERO)) { "First basal schedule entry should have 0 offset" }
    }

    fun rateAt(offset: Duration): Double = lookup(offset).basalScheduleEntry.rate
    fun getEntries(): List<BasalScheduleEntry> = ArrayList(entries)

    private fun lookup(offset: Duration): BasalScheduleLookupResult {
        require(!(offset.isLongerThan(Duration.standardHours(24)) || offset.isShorterThan(Duration.ZERO))) { "Invalid duration" }
        val reversedBasalScheduleEntries = reversedBasalScheduleEntries()
        var last = Duration.standardHours(24)
        for ((index, entry) in reversedBasalScheduleEntries.withIndex()) {
            if (entry.startTime.isShorterThan(offset) || entry.startTime == offset)
                return BasalScheduleLookupResult(reversedBasalScheduleEntries.size - (index + 1), entry, entry.startTime, last.minus(entry.startTime))
            last = entry.startTime
        }
        throw IllegalArgumentException("Basal schedule incomplete")
    }

    private fun reversedBasalScheduleEntries(): List<BasalScheduleEntry> = ArrayList(entries).toMutableList().also { it.reverse() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as BasalSchedule
        return entries == that.entries
    }

    override fun hashCode(): Int = Objects.hash(entries)
    class BasalScheduleLookupResult internal constructor(val index: Int, val basalScheduleEntry: BasalScheduleEntry, val startTime: Duration, val duration: Duration)
    companion object {

        fun mapProfileToBasalSchedule(profile: Profile): BasalSchedule {
            val entries = ArrayList<BasalScheduleEntry>()
            for (i in 0..23) {
                val value = profile.getBasalTimeFromMidnight(i * 60 * 60)
                val basalScheduleEntry = BasalScheduleEntry(value, Duration.standardSeconds((i * 60 * 60L)))
                entries.add(basalScheduleEntry)
            }
            return BasalSchedule(entries)
        }
    }
}
