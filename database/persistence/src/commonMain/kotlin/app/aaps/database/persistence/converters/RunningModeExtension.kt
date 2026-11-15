package app.aaps.database.persistence.converters

import app.aaps.core.data.model.RM
import app.aaps.database.entities.RunningMode

fun RunningMode.Mode.fromDb(): RM.Mode =
    when (this) {
        RunningMode.Mode.DISABLED_LOOP     -> RM.Mode.DISABLED_LOOP
        RunningMode.Mode.OPEN_LOOP         -> RM.Mode.OPEN_LOOP
        RunningMode.Mode.CLOSED_LOOP       -> RM.Mode.CLOSED_LOOP
        RunningMode.Mode.CLOSED_LOOP_LGS   -> RM.Mode.CLOSED_LOOP_LGS
        RunningMode.Mode.SUPER_BOLUS       -> RM.Mode.SUPER_BOLUS
        RunningMode.Mode.DISCONNECTED_PUMP -> RM.Mode.DISCONNECTED_PUMP
        RunningMode.Mode.SUSPENDED_BY_PUMP -> RM.Mode.SUSPENDED_BY_PUMP
        RunningMode.Mode.SUSPENDED_BY_USER -> RM.Mode.SUSPENDED_BY_USER
        RunningMode.Mode.SUSPENDED_BY_DST -> RM.Mode.SUSPENDED_BY_DST
    }

fun RM.Mode.toDb(): RunningMode.Mode =
    when (this) {
        RM.Mode.DISABLED_LOOP     -> RunningMode.Mode.DISABLED_LOOP
        RM.Mode.OPEN_LOOP         -> RunningMode.Mode.OPEN_LOOP
        RM.Mode.CLOSED_LOOP       -> RunningMode.Mode.CLOSED_LOOP
        RM.Mode.CLOSED_LOOP_LGS   -> RunningMode.Mode.CLOSED_LOOP_LGS
        RM.Mode.SUPER_BOLUS       -> RunningMode.Mode.SUPER_BOLUS
        RM.Mode.DISCONNECTED_PUMP -> RunningMode.Mode.DISCONNECTED_PUMP
        RM.Mode.SUSPENDED_BY_PUMP -> RunningMode.Mode.SUSPENDED_BY_PUMP
        RM.Mode.SUSPENDED_BY_USER -> RunningMode.Mode.SUSPENDED_BY_USER
        RM.Mode.SUSPENDED_BY_DST -> RunningMode.Mode.SUSPENDED_BY_DST
        RM.Mode.RESUME            -> error("Invalid mode")
    }

fun RunningMode.fromDb(): RM =
    RM(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        ids = this.interfaceIDs.fromDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        mode = this.mode.fromDb(),
        autoForced = this.autoForced,
        reasons = this.reasons,
        duration = this.duration
    )

fun RM.toDb(): RunningMode =
    RunningMode(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        interfaceIDs_backing = this.ids.toDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        mode = this.mode.toDb(),
        autoForced = this.autoForced,
        reasons = this.reasons,
        duration = this.duration
    )

