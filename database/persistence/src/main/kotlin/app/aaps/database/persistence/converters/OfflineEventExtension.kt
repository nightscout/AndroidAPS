package app.aaps.database.persistence.converters

import app.aaps.core.data.model.OE
import app.aaps.database.entities.OfflineEvent

fun OfflineEvent.Reason.fromDb(): OE.Reason =
    when (this) {
        OfflineEvent.Reason.DISCONNECT_PUMP -> OE.Reason.DISCONNECT_PUMP
        OfflineEvent.Reason.SUSPEND         -> OE.Reason.SUSPEND
        OfflineEvent.Reason.DISABLE_LOOP    -> OE.Reason.DISABLE_LOOP
        OfflineEvent.Reason.SUPER_BOLUS     -> OE.Reason.SUPER_BOLUS
        OfflineEvent.Reason.OTHER           -> OE.Reason.OTHER
    }

fun OE.Reason.toDb(): OfflineEvent.Reason =
    when (this) {
        OE.Reason.DISCONNECT_PUMP -> OfflineEvent.Reason.DISCONNECT_PUMP
        OE.Reason.SUSPEND         -> OfflineEvent.Reason.SUSPEND
        OE.Reason.DISABLE_LOOP    -> OfflineEvent.Reason.DISABLE_LOOP
        OE.Reason.SUPER_BOLUS     -> OfflineEvent.Reason.SUPER_BOLUS
        OE.Reason.OTHER           -> OfflineEvent.Reason.OTHER
    }

fun OfflineEvent.fromDb(): OE =
    OE(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        ids = this.interfaceIDs.fromDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.fromDb(),
        duration = this.duration
    )

fun OE.toDb(): OfflineEvent =
    OfflineEvent(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        interfaceIDs_backing = this.ids.toDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        reason = this.reason.toDb(),
        duration = this.duration
    )

