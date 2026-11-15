package app.aaps.database.persistence.converters

import app.aaps.core.data.model.CAL
import app.aaps.database.entities.CalibrationEntry

fun CalibrationEntry.fromDb(): CAL =
    CAL(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        fingerstickMgdl = this.fingerstickMgdl,
        sensorMgdlAtPairing = this.sensorMgdlAtPairing,
        ids = this.interfaceIDs.fromDb()
    )

fun CAL.toDb(): CalibrationEntry =
    CalibrationEntry(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        fingerstickMgdl = this.fingerstickMgdl,
        sensorMgdlAtPairing = this.sensorMgdlAtPairing,
        interfaceIDs_backing = this.ids.toDb()
    )
