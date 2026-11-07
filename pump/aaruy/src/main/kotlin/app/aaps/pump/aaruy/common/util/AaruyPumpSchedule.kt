package app.aaps.pump.aaruy.common.util

import app.aaps.pump.aaruy.comm.BasalItem
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Measured on a real pump: it runs its basal schedule on UTC+8, whatever the time zone of the phone is. Its
 * `timeZoneCorrection` setting changes nothing, tried in hours and in 15 and 30 minute steps. So the driver moves the
 * schedule: a rate for 23:00 in Prague goes to the pump as a rate for 06:00.
 *
 * A start is in half hours, as the pump keeps it. A zone with a 15 minute offset is 15 minutes off, and that cannot be better.
 */
object AaruyPumpSchedule {

    private const val HALF_HOURS_PER_DAY = 48
    private const val HALF_HOUR_MS = 30 * 60 * 1000.0
    private const val PUMP_ZONE_OFFSET_MS = 8 * 60 * 60 * 1000

    /** How many half hours the clock of the pump is ahead of the local clock at this moment. */
    fun shift(now: Long, zone: TimeZone = TimeZone.getDefault()): Int = ((PUMP_ZONE_OFFSET_MS - zone.getOffset(now)) / HALF_HOUR_MS).roundToInt()

    fun toPump(local: List<BasalItem>, shift: Int): List<BasalItem> = rotate(local, shift)

    fun toLocal(pump: List<BasalItem>, shift: Int): List<BasalItem> = rotate(pump, -shift)

    /** The first start is always 0. Equal neighbours become one, or a flat profile would look different in every time zone. */
    private fun rotate(items: List<BasalItem>, shift: Int): List<BasalItem> {
        if (items.isEmpty()) return items
        val moved = items.map { BasalItem(Math.floorMod(it.startTime + shift, HALF_HOURS_PER_DAY), it.basalSpeed) }.sortedBy { it.startTime }
        val fromMidnight = if (moved.first().startTime == 0) moved else listOf(BasalItem(0, moved.last().basalSpeed)) + moved
        return fromMidnight.fold(emptyList()) { merged, item -> if (merged.lastOrNull()?.basalSpeed == item.basalSpeed) merged else merged + item }
    }

    /** The insulin of one day, in units. */
    fun dailyTotal(items: List<BasalItem>): Double =
        items.indices.sumOf { index ->
            val end = items.getOrNull(index + 1)?.startTime ?: HALF_HOURS_PER_DAY
            items[index].basalSpeed * (end - items[index].startTime) / 2.0
        }
}
