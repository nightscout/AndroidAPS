package info.nightscout.core.extensions

import app.aaps.interfaces.db.GlucoseUnit
import app.aaps.interfaces.utils.DateUtil
import info.nightscout.database.entities.TherapyEvent

fun TherapyEvent.isOlderThan(hours: Double, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

fun TherapyEvent.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL
