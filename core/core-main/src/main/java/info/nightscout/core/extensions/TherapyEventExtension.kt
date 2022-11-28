package info.nightscout.core.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.GlucoseUnit

fun TherapyEvent.isOlderThan(hours: Double): Boolean {
    return getHoursFromStart() > hours
}

fun TherapyEvent.getHoursFromStart(): Double {
    return (System.currentTimeMillis() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL
