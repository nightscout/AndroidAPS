package app.aaps.core.objects.extensions

import app.aaps.core.data.db.GV
import app.aaps.core.data.iob.InMemoryGlucoseValue

fun InMemoryGlucoseValue.Companion.fromGv(gv: GV): InMemoryGlucoseValue =
    InMemoryGlucoseValue(timestamp = gv.timestamp, value = gv.value, trendArrow = gv.trendArrow, sourceSensor = gv.sourceSensor)

