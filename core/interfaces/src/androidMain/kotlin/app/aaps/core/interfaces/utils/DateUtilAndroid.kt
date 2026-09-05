package app.aaps.core.interfaces.utils

import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.resources.ResourceHelper
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Formats an age as "2 days 3 hours ago".
 *
 * This is an Android extension rather than a [DateUtil] member because it needs plurals, and a
 * [app.aaps.core.keys.interfaces.TextRef] cannot carry a plural today. Everything else on [DateUtil]
 * resolves through [app.aaps.core.interfaces.resources.TextResolver] and works on any platform.
 */
fun timeAgoFullString(milliseconds: Long, rh: ResourceHelper): String =
    when {
        milliseconds <= 0 -> ""

        else              -> {
            val duration = milliseconds.milliseconds
            val days = duration.inWholeDays
            val hours = (duration - days.days).inWholeHours
            val minutes = (duration - days.days - hours.hours).inWholeMinutes
            when {
                days > 0    -> rh.gq(R.plurals.plurals_day_hour_ago, days.toInt(), days.toString(), hours.toString())
                hours > 0   -> rh.gq(R.plurals.plurals_hour_ago, hours.toInt(), hours.toString())
                minutes > 0 -> rh.gq(R.plurals.plurals_minute_ago, minutes.toInt(), minutes.toString())
                else        -> rh.gs(R.string.seconds_ago)
            }
        }
    }
