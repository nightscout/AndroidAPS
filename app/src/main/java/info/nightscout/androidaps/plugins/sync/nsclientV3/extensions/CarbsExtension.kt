package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.database.entities.Carbs

fun info.nightscout.sdk.localmodel.treatment.Carbs.toCarbs() : Carbs =
    Carbs(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        amount = carbs,
        notes = notes,
        duration = duration
    )
