package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TT
import app.aaps.database.entities.TemporaryTarget

fun TemporaryTarget.Reason.fromDb(): TT.Reason =
    when (this) {
        TemporaryTarget.Reason.CUSTOM       -> TT.Reason.CUSTOM
        TemporaryTarget.Reason.HYPOGLYCEMIA -> TT.Reason.HYPOGLYCEMIA
        TemporaryTarget.Reason.ACTIVITY     -> TT.Reason.ACTIVITY
        TemporaryTarget.Reason.EATING_SOON  -> TT.Reason.EATING_SOON
        TemporaryTarget.Reason.AUTOMATION   -> TT.Reason.AUTOMATION
        TemporaryTarget.Reason.WEAR         -> TT.Reason.WEAR
    }

fun TT.Reason.toDb(): TemporaryTarget.Reason =
    when (this) {
        TT.Reason.CUSTOM       -> TemporaryTarget.Reason.CUSTOM
        TT.Reason.HYPOGLYCEMIA -> TemporaryTarget.Reason.HYPOGLYCEMIA
        TT.Reason.ACTIVITY     -> TemporaryTarget.Reason.ACTIVITY
        TT.Reason.EATING_SOON  -> TemporaryTarget.Reason.EATING_SOON
        TT.Reason.AUTOMATION   -> TemporaryTarget.Reason.AUTOMATION
        TT.Reason.WEAR         -> TemporaryTarget.Reason.WEAR
    }

fun TemporaryTarget.fromDb(): TT =
    TT(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        ids = this.interfaceIDs.fromDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.fromDb(),
        highTarget = this.highTarget,
        lowTarget = this.lowTarget,
        duration = this.duration
    )

fun TT.toDb(): TemporaryTarget =
    TemporaryTarget(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        interfaceIDs_backing = this.ids.toDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.toDb(),
        highTarget = this.highTarget,
        lowTarget = this.lowTarget,
        duration = this.duration
    )
