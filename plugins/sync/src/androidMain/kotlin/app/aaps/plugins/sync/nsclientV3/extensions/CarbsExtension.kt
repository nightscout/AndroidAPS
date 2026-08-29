package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.CA
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import java.security.InvalidParameterException
import kotlin.math.min

fun NSCarbs.toCarbs(): CA =
    CA(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = min(carbs, HardLimits.MAX_CARBS.toDouble()),
        notes = notes,
        duration = min(duration ?: 0L, T.hours(HardLimits.MAX_CARBS_DURATION_HOURS).msecs()),
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun CA.toNSCarbs(): NSCarbs =
    NSCarbs(
        eventType = if (amount < 12) EventType.CARBS_CORRECTION else EventType.MEAL_BOLUS,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        carbs = amount,
        notes = notes,
        duration = if (duration != 0L) duration else null,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )
