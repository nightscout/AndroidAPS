package app.aaps.core.main.extensions

import app.aaps.data.iob.InMemoryGlucoseValue
import app.aaps.database.entities.GlucoseValue

fun InMemoryGlucoseValue(gv: GlucoseValue): InMemoryGlucoseValue =
    InMemoryGlucoseValue(timestamp = gv.timestamp, value = gv.value, trendArrow = gv.trendArrow.fromDb(), sourceSensor = gv.sourceSensor.fromDb())

