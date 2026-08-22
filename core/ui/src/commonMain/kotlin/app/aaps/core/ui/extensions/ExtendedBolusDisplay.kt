package app.aaps.core.ui.extensions

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.getPassedDurationToTimeInMinutes
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.UiStrings

/**
 * Text for an extended bolus on screen. The record's own maths stays in `:core:objects` - see
 * [TB.toStringFull] for why the split runs where it does.
 */
fun EB.toStringFull(dateUtil: DateUtil, rh: TextResolver): String =
    rh.gs(UiStrings.extended_bolus_full, rate, dateUtil.timeString(timestamp), getPassedDurationToTimeInMinutes(dateUtil.now()), T.msecs(duration).mins())

fun EB.toStringFull(dateUtil: DateUtil, ch: ConcentrationHelper): String =
    "${ch.basalRateString(PumpRate(rate), true)} ${dateUtil.timeString(timestamp)} ${getPassedDurationToTimeInMinutes(dateUtil.now())}/${T.msecs(duration).mins()}"

fun EB.toStringMedium(dateUtil: DateUtil, rh: TextResolver): String =
    rh.gs(UiStrings.extended_bolus_medium, rate, getPassedDurationToTimeInMinutes(dateUtil.now()), T.msecs(duration).mins())
