package app.aaps.plugins.sync.nsShared.extensions

import app.aaps.core.data.model.GV

fun GV.contentEqualsTo(other: GV): Boolean =
    isValid == other.isValid &&
        timestamp == other.timestamp &&
        utcOffset == other.utcOffset &&
        raw == other.raw &&
        value == other.value &&
        trendArrow == other.trendArrow &&
        noise == other.noise &&
        sourceSensor == other.sourceSensor

fun GV.onlyNsIdAdded(previous: GV): Boolean =
    previous.id != id &&
        contentEqualsTo(previous) &&
        previous.ids.nightscoutId == null &&
        ids.nightscoutId != null

