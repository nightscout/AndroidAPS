package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import java.security.InvalidParameterException

fun NSBolus.toBolus(): BS =
    BS(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = insulin,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSBolus.BolusType?.toBolusType(): BS.Type =
    BS.Type.fromString(this?.name)

fun BS.toNSBolus(): NSBolus =
    NSBolus(
        eventType = if (type == BS.Type.SMB) EventType.CORRECTION_BOLUS else EventType.MEAL_BOLUS,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        insulin = amount,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )

fun BS.Type?.toBolusType(): NSBolus.BolusType =
    NSBolus.BolusType.fromString(this?.name)
