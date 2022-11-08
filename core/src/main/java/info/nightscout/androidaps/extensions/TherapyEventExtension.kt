package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.GlucoseUnit

fun TherapyEvent.isOlderThan(hours: Double): Boolean {
    return getHoursFromStart() > hours
}

fun TherapyEvent.getHoursFromStart(): Double {
    return (System.currentTimeMillis() - timestamp) / (60 * 60 * 1000.0)
}

fun TherapyEvent.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): TherapyEvent.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) TherapyEvent.GlucoseUnit.MGDL
    else TherapyEvent.GlucoseUnit.MMOL
