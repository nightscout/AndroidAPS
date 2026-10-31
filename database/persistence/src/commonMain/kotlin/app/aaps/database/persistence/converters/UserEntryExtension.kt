package app.aaps.database.persistence.converters

import app.aaps.core.data.model.UE
import app.aaps.database.entities.UserEntry

fun UserEntry.fromDb(): UE =
    UE(
        id = this.id,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        action = this.action.fromDb(),
        source = this.source.fromDb(),
        note = this.note,
        values = this.values.fromDb()
    )

fun UE.toDb(): UserEntry =
    UserEntry(
        id = this.id,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        action = this.action.toDb(),
        source = this.source.toDb(),
        note = this.note,
        values = this.values.toDb()
    )