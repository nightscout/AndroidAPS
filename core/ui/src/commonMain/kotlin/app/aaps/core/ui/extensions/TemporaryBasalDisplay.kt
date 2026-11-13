package app.aaps.core.ui.extensions

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.durationInMinutes
import app.aaps.core.data.model.getPassedDurationToTimeInMinutes
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.CoreUiStrings

/**
 * Text for a temporary basal on screen.
 *
 * The maths that belongs to the record - `iobCalc`, `convertedToAbsolute`, `convertedToPercent` -
 * stays in `:core:objects`. Only the part that formats for a reader lives here, next to the strings
 * it uses. The time helpers it needs are in `:core:data` for that reason: `:core:ui` cannot depend on
 * `:core:objects`, which depends on this module.
 */
private fun TB.netExtendedRate(profile: Profile) = rate - profile.getBasal(timestamp)

/**
 * @param rateFormat the digits of a rate: `PumpDescription.tempAbsoluteFormat` of the active pump. A pump with a step of
 * 0.025 needs 3 digits, or 0.275 U/h is shown as 0.28 U/h. The default is the old fixed 2 digits.
 */
fun TB.toStringFull(profile: Profile, dateUtil: DateUtil, rh: TextResolver, rateFormat: NumberFormat = TWO_DIGITS): String {
    val timeAndDuration = "${dateUtil.timeString(timestamp)} ${getPassedDurationToTimeInMinutes(dateUtil.now())}/${durationInMinutes}'"

    return when {
        type == TB.Type.FAKE_EXTENDED -> rh.gs(CoreUiStrings.temp_basal_tsf_fake_extended_dynamic, rateFormat.format(rate), rateFormat.format(netExtendedRate(profile)), timeAndDuration)
        isAbsolute                    -> rh.gs(CoreUiStrings.temp_basal_tsf_absolute_dynamic, rateFormat.format(rate), timeAndDuration)
        else                          -> rh.gs(CoreUiStrings.temp_basal_tsf_percent, rate, timeAndDuration)
    }
}

/** What `%.2f` gave: always 2 digits, a tie rounded up. */
private val TWO_DIGITS = NumberFormat.withDecimalsHalfUp(2)

fun TB.toStringFull(profile: Profile, dateUtil: DateUtil, ch: ConcentrationHelper): String {
    val timeAndDuration = "${dateUtil.timeString(timestamp)} ${getPassedDurationToTimeInMinutes(dateUtil.now())}/${durationInMinutes}'"

    return when {
        type == TB.Type.FAKE_EXTENDED -> "${ch.basalRateString(PumpRate(rate), true)} (${netExtendedRate(profile)}E) $timeAndDuration"
        isAbsolute                    -> "${ch.basalRateString(PumpRate(rate), true)} $timeAndDuration"
        else                          -> "${ch.basalRateString(PumpRate(rate), false)} $timeAndDuration"
    }
}

/** @param rateFormat see [toStringFull] */
fun TB.toStringShort(rh: TextResolver, rateFormat: NumberFormat = TWO_DIGITS): String =
    if (isAbsolute || type == TB.Type.FAKE_EXTENDED) rh.gs(CoreUiStrings.pump_base_basal_rate_dynamic, rateFormat.format(rate))
    else rh.gs(CoreUiStrings.formatPercent, rate)
