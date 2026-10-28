package app.aaps.plugins.source.compose

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * The age of a CGM sensor, as shown in "Started %1$s ago" on the ONE+ screens.
 *
 * It keeps the two largest units, like the old `DateUtil.age(millis, false, rh)` did:
 * "2 days, 5 hours", "3 hours, 10 minutes", "7 minutes". Every piece comes from a translated
 * template (`time_value_with_unit`, `values_with_comma`), so the translator controls the order and
 * the separator. A negative age (clock moved back) is shown as 0 minutes.
 */
fun formatSensorAge(millis: Long, rh: TextResolver): String {
    val duration = millis.coerceAtLeast(0L).milliseconds
    val days = duration.inWholeDays
    val hours = (duration - days.days).inWholeHours
    val minutes = (duration - days.days - hours.hours).inWholeMinutes
    return when {
        days > 0  -> rh.gs(
            InterfacesStrings.values_with_comma,
            valueWithUnit(days, InterfacesStrings.unit_day, InterfacesStrings.unit_days, rh),
            valueWithUnit(hours, InterfacesStrings.unit_hour, InterfacesStrings.unit_hours, rh)
        )

        hours > 0 -> rh.gs(
            InterfacesStrings.values_with_comma,
            valueWithUnit(hours, InterfacesStrings.unit_hour, InterfacesStrings.unit_hours, rh),
            valueWithUnit(minutes, InterfacesStrings.unit_minute, InterfacesStrings.unit_minutes, rh)
        )

        else      -> valueWithUnit(minutes, InterfacesStrings.unit_minute, InterfacesStrings.unit_minutes, rh)
    }
}

private fun valueWithUnit(value: Long, one: TextRef, many: TextRef, rh: TextResolver): String =
    rh.gs(InterfacesStrings.time_value_with_unit, value.toString(), rh.gs(if (value == 1L) one else many))
