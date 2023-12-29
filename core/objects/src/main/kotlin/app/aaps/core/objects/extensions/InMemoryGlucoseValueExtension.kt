package app.aaps.core.objects.extensions

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV

fun InMemoryGlucoseValue.Companion.fromGv(gv: GV): InMemoryGlucoseValue =
    InMemoryGlucoseValue(timestamp = gv.timestamp, value = gv.value, trendArrow = gv.trendArrow, sourceSensor = gv.sourceSensor)

