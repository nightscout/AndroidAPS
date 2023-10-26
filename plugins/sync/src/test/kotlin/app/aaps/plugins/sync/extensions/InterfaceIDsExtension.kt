package app.aaps.plugins.sync.extensions

import app.aaps.core.data.model.IDs

fun IDs.contentEqualsTo(other: IDs): Boolean =
    nightscoutId == other.nightscoutId &&
        nightscoutSystemId == other.nightscoutSystemId &&
        pumpType == other.pumpType &&
        pumpSerial == other.pumpSerial &&
        temporaryId == other.temporaryId &&
        pumpId == other.pumpId &&
        startId == other.startId &&
        endId == other.endId

