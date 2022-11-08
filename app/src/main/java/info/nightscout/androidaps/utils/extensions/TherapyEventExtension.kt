package info.nightscout.androidaps.utils.extensions

import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.plugins.sync.nsclient.data.NSMbg

fun therapyEventFromNsMbg(mbg: NSMbg) =
    TherapyEvent(
        type = TherapyEvent.Type.FINGER_STICK_BG_VALUE, //convert Mbg to finger stick because is coming from "entries" collection
        timestamp = mbg.date,
        glucose = mbg.mbg,
        glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
    )
