package app.aaps.core.interfaces.utils

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper

/**
 * The strings [readableDuration] needs. Each caller passes its own set, so a driver keeps the wording
 * and the translations it already has.
 *
 * @param momentsAgo shown under ten seconds
 * @param lessThanAMinuteAgo shown under a minute
 * @param timeAgo wraps the amount, for example `%1$s ago`
 * @param compositeTime joins two amounts, for example `%1$s and %2$s`
 * @param minutes plural for minutes, taking the count as its only argument
 * @param hours plural for hours
 * @param days plural for days
 */
class DurationLabels(
    @StringRes val momentsAgo: Int,
    @StringRes val lessThanAMinuteAgo: Int,
    @StringRes val timeAgo: Int,
    @StringRes val compositeTime: Int,
    @PluralsRes val minutes: Int,
    @PluralsRes val hours: Int,
    @PluralsRes val days: Int
)

/**
 * Formats how long ago something happened, as "5 minutes ago" or "2 hours and 10 minutes ago".
 *
 * The Omnipod Eros, Omnipod Dash and Equil overviews each carried their own copy of this, identical
 * apart from the resource names, which is also where most of the project's plural use sat. It lives
 * here once now, and it is the only place that has to change when plurals get a
 * [app.aaps.core.keys.interfaces.TextRef] form - a plural cannot be expressed as a TextRef today.
 *
 * It stays on Android because plural selection must come from the platform: plural categories are not
 * just one and other. Czech has one, few, many and other; Arabic has six. `getQuantityString` uses the
 * platform CLDR data, and iOS would use a `.stringsdict` the same way. Shared code must never pick the
 * form itself.
 *
 * @param millis how long ago, in milliseconds
 */
fun ResourceHelper.readableDuration(millis: Long, labels: DurationLabels): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        seconds < 10          -> gs(labels.momentsAgo)
        seconds < 60          -> gs(labels.lessThanAMinuteAgo)
        seconds < 60 * 60     -> gs(labels.timeAgo, gq(labels.minutes, minutes.toInt(), minutes.toInt()))

        seconds < 24 * 60 * 60 -> {
            val minutesLeft = (minutes % 60).toInt()
            if (minutesLeft > 0)
                gs(
                    labels.timeAgo,
                    gs(labels.compositeTime, gq(labels.hours, hours.toInt(), hours.toInt()), gq(labels.minutes, minutesLeft, minutesLeft))
                )
            else
                gs(labels.timeAgo, gq(labels.hours, hours.toInt(), hours.toInt()))
        }

        else                  -> {
            val days = (hours / 24).toInt()
            val hoursLeft = (hours % 24).toInt()
            if (hoursLeft > 0)
                gs(
                    labels.timeAgo,
                    gs(labels.compositeTime, gq(labels.days, days, days), gq(labels.hours, hoursLeft, hoursLeft))
                )
            else
                gs(labels.timeAgo, gq(labels.days, days, days))
        }
    }
}
