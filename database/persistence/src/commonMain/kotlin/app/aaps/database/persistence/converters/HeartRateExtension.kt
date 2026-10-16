package app.aaps.database.persistence.converters

import app.aaps.core.data.model.HR
import app.aaps.database.entities.HeartRate

fun HeartRate.fromDb(): HR =
    HR(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        beatsPerMinute = this.beatsPerMinute,
        device = this.device,
        ids = this.interfaceIDs.fromDb()
    )

fun HR.toDb(): HeartRate =
    HeartRate(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        beatsPerMinute = this.beatsPerMinute,
        device = this.device,
        interfaceIDs_backing = this.ids.toDb()
    )
