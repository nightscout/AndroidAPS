package app.aaps.core.main.extensions

import app.aaps.core.data.db.IDs
import app.aaps.core.main.pump.fromDb
import app.aaps.core.main.pump.toDb
import app.aaps.database.entities.embedments.InterfaceIDs

fun InterfaceIDs.fromDb(): IDs =
    IDs(
        nightscoutSystemId = this.nightscoutSystemId,
        nightscoutId = this.nightscoutId,
        pumpType = this.pumpType?.fromDb(),
        pumpSerial = this.pumpSerial,
        temporaryId = this.temporaryId,
        pumpId = this.pumpId,
        startId = this.startId,
        endId = this.endId
    )

fun IDs.toDb(): InterfaceIDs =
    InterfaceIDs(
        nightscoutSystemId = this.nightscoutSystemId,
        nightscoutId = this.nightscoutId,
        pumpType = this.pumpType?.toDb(),
        pumpSerial = this.pumpSerial,
        temporaryId = this.temporaryId,
        pumpId = this.pumpId,
        startId = this.startId,
        endId = this.endId
    )

fun IDs.contentEqualsTo(other: IDs): Boolean =
    nightscoutId == other.nightscoutId &&
        nightscoutSystemId == other.nightscoutSystemId &&
        pumpType == other.pumpType &&
        pumpSerial == other.pumpSerial &&
        temporaryId == other.temporaryId &&
        pumpId == other.pumpId &&
        startId == other.startId &&
        endId == other.endId

