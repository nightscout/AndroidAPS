package app.aaps.pump.omnipod.common

import app.aaps.core.interfaces.utils.DurationLabels

/**
 * The Omnipod wording for [app.aaps.core.interfaces.utils.readableDuration], shared by Eros and Dash.
 * Both drivers showed the same texts through their own copy of the formatter before.
 */
val OMNIPOD_DURATION_LABELS = DurationLabels(
    momentsAgo = R.string.omnipod_common_moments_ago,
    lessThanAMinuteAgo = R.string.omnipod_common_less_than_a_minute_ago,
    timeAgo = R.string.omnipod_common_time_ago,
    compositeTime = R.string.omnipod_common_composite_time,
    minutes = R.plurals.omnipod_common_minutes,
    hours = R.plurals.omnipod_common_hours,
    days = R.plurals.omnipod_common_days
)
