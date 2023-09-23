package info.nightscout.core.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.shared.utils.DateUtil

fun TherapyEvent.isOlderThan(hours: Double, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

fun TherapyEvent.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL
