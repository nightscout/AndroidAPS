package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TB
import app.aaps.database.entities.TemporaryBasal

fun TemporaryBasal.fromDb(): TB =
    TB(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        type = type.fromDb(),
        isAbsolute = this.isAbsolute,
        rate = this.rate,
        duration = this.duration,
        ids = this.interfaceIDs.fromDb()
    )

fun TB.toDb(): TemporaryBasal =
    TemporaryBasal(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        type = type.toDb(),
        isAbsolute = this.isAbsolute,
        rate = this.rate,
        duration = this.duration,
        interfaceIDs_backing = this.ids.toDb()
    )

fun TemporaryBasal.Type.fromDb(): TB.Type =
    when (this) {
        TemporaryBasal.Type.NORMAL                -> TB.Type.NORMAL
        TemporaryBasal.Type.EMULATED_PUMP_SUSPEND -> TB.Type.EMULATED_PUMP_SUSPEND
        TemporaryBasal.Type.PUMP_SUSPEND          -> TB.Type.PUMP_SUSPEND
        TemporaryBasal.Type.SUPERBOLUS            -> TB.Type.SUPERBOLUS
        TemporaryBasal.Type.FAKE_EXTENDED         -> TB.Type.FAKE_EXTENDED
    }

fun TB.Type.toDb(): TemporaryBasal.Type =
    when (this) {
        TB.Type.NORMAL                -> TemporaryBasal.Type.NORMAL
        TB.Type.EMULATED_PUMP_SUSPEND -> TemporaryBasal.Type.EMULATED_PUMP_SUSPEND
        TB.Type.PUMP_SUSPEND          -> TemporaryBasal.Type.PUMP_SUSPEND
        TB.Type.SUPERBOLUS            -> TemporaryBasal.Type.SUPERBOLUS
        TB.Type.FAKE_EXTENDED         -> TemporaryBasal.Type.FAKE_EXTENDED
    }