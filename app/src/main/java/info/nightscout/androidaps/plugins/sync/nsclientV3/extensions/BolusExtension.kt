package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.database.entities.Bolus

fun info.nightscout.sdk.localmodel.treatment.Bolus.toBolus() : Bolus =
    Bolus(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        amount = insulin,
        type = type.toBolusType(),
        notes = notes,
    )

fun info.nightscout.sdk.localmodel.treatment.Bolus.BolusType?.toBolusType() : Bolus.Type =
    when(this) {
        info.nightscout.sdk.localmodel.treatment.Bolus.BolusType.NORMAL  -> Bolus.Type.NORMAL
        info.nightscout.sdk.localmodel.treatment.Bolus.BolusType.SMB     -> Bolus.Type.SMB
        info.nightscout.sdk.localmodel.treatment.Bolus.BolusType.PRIMING -> Bolus.Type.PRIMING
        null                                                             -> Bolus.Type.NORMAL
    }