package app.aaps.database.persistence.converters

import app.aaps.core.data.model.EB
import app.aaps.database.entities.ExtendedBolus

fun ExtendedBolus.fromDb(): EB =
    EB(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        amount = this.amount,
        ids = this.interfaceIDs.fromDb()
    )

fun EB.toDb(): ExtendedBolus =
    ExtendedBolus(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        amount = this.amount,
        interfaceIDs_backing = this.ids.toDb()
    )
