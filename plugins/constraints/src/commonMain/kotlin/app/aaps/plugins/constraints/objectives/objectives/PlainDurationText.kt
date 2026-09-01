package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.data.time.T
import kotlin.math.floor

/**
 * [DurationText] without plural forms, for the platforms that have no plural table.
 *
 * Says "3 d", "5 h", "20 min". Deliberately not translated: a made-up singular and plural would be
 * wrong in most languages, and a unit abbreviation that is obviously untranslated is easier to spot
 * and fix than one that looks finished.
 *
 * Carries no DI annotation on purpose. Android must **not** get this - it has real plurals - so each
 * platform that wants it provides it, rather than it contributing itself to every graph.
 */
class PlainDurationText : DurationText {

    override fun format(durationMs: Long): String {
        val days = floor(durationMs.toDouble() / T.days(1).msecs()).toInt()
        val hours = floor(durationMs.toDouble() / T.hours(1).msecs()).toInt()
        val minutes = floor(durationMs.toDouble() / T.mins(1).msecs()).toInt()
        return when {
            days > 0  -> "$days d"
            hours > 0 -> "$hours h"
            else      -> "$minutes min"
        }
    }
}
