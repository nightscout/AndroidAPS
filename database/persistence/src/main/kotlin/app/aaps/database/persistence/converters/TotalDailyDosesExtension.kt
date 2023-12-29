package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TDD
import app.aaps.database.entities.TotalDailyDose

fun TotalDailyDose.fromDb(): TDD =
    TDD(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        basalAmount = this.basalAmount,
        bolusAmount = this.bolusAmount,
        totalAmount = this.totalAmount,
        carbs = this.carbs,
        ids = this.interfaceIDs.fromDb()
    )

fun TDD.toDb(): TotalDailyDose =
    TotalDailyDose(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        basalAmount = this.basalAmount,
        bolusAmount = this.bolusAmount,
        totalAmount = this.totalAmount,
        carbs = this.carbs,
        interfaceIDs_backing = this.ids.toDb()
    )
