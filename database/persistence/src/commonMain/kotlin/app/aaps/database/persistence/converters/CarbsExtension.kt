package app.aaps.database.persistence.converters

import app.aaps.core.data.model.CA
import app.aaps.database.entities.Carbs

fun Carbs.fromDb(): CA =
    CA(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        amount = this.amount,
        notes = this.notes,
        ids = this.interfaceIDs.fromDb()
    )

fun CA.toDb(): Carbs =
    Carbs(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        amount = this.amount,
        notes = this.notes,
        interfaceIDs_backing = this.ids.toDb()
    )
