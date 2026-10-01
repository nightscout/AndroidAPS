package app.aaps.database.persistence.converters

import app.aaps.core.data.model.SC
import app.aaps.database.entities.StepsCount

fun StepsCount.fromDb(): SC =
    SC(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        steps5min = this.steps5min,
        steps10min = this.steps10min,
        steps15min = this.steps15min,
        steps30min = this.steps30min,
        steps60min = this.steps60min,
        steps180min = this.steps180min,
        device = this.device,
        ids = this.interfaceIDs.fromDb()
    )

fun SC.toDb(): StepsCount =
    StepsCount(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        duration = this.duration,
        steps5min = this.steps5min,
        steps10min = this.steps10min,
        steps15min = this.steps15min,
        steps30min = this.steps30min,
        steps60min = this.steps60min,
        steps180min = this.steps180min,
        device = this.device,
        interfaceIDs_backing = this.ids.toDb()
    )
