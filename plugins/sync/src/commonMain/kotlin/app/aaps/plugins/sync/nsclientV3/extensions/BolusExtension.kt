package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSICfg

/** [insulinFallback] stamps records uploaded without one — see `NsIncomingDataProcessor.fallbackICfg`. */
fun NSBolus.toBolus(insulinFallback: ICfg): BS {
    val iCfg =
        iCfg?.let {
            ICfg(insulinLabel = it.insulinLabel, insulinEndTime = it.insulinEndTime, insulinPeakTime = it.insulinPeakTime, concentration = it.concentration)
        } ?: insulinFallback
    return BS(
        isValid = isValid,
        timestamp = date ?: throw IllegalArgumentException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = insulin,
        type = type.toBolusType(),
        notes = notes,
        isBasalInsulin = isBasalInsulin,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId),
        iCfg = iCfg
    )
}

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
        endId = ids.endId,
        iCfg = NSICfg(insulinLabel = iCfg.insulinLabel, insulinEndTime = iCfg.insulinEndTime, insulinPeakTime = iCfg.insulinPeakTime, concentration = iCfg.concentration)
    )

fun BS.Type?.toBolusType(): NSBolus.BolusType =
    NSBolus.BolusType.fromString(this?.name)
