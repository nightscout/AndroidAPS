package app.aaps.core.main.extensions

import app.aaps.core.data.db.OE
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

