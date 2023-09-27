package app.aaps.core.main.extensions

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.TherapyEvent

fun TherapyEvent.isOlderThan(hours: Double, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

fun TherapyEvent.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL
