package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.nssdk.localmodel.clientcontrol.BatchActionDto
import app.aaps.core.objects.extensions.fromJsonObject
import app.aaps.core.objects.extensions.toJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Domain [BatchAction] → wire [BatchActionDto] (client send side). */
internal fun BatchAction.toDto(): BatchActionDto = when (this) {
    is BatchAction.Bolus               -> BatchActionDto(
        type = BatchActionDto.TYPE_BOLUS,
        insulin = insulin, carbs = carbs, carbsTimeOffsetMinutes = carbsTimeOffsetMinutes,
        carbsDurationHours = carbsDurationHours, recordOnly = recordOnly, notes = notes, timestamp = timestamp,
        iCfgJson = iCfg?.toJsonObject()?.toString(),
        eCarbsGrams = eCarbsGrams, eCarbsDelayMinutes = eCarbsDelayMinutes, eCarbsDurationHours = eCarbsDurationHours,
        quickWizardGuid = quickWizardGuid
    )

    is BatchAction.TempTarget          -> BatchActionDto(
        type = BatchActionDto.TYPE_TEMP_TARGET,
        reason = reason, lowMgdl = lowMgdl, highMgdl = highMgdl, durationMinutes = durationMinutes, startOffsetMinutes = startOffsetMinutes,
        notes = notes ?: ""
    )

    is BatchAction.ProfileSwitch       -> BatchActionDto(
        type = BatchActionDto.TYPE_PROFILE_SWITCH,
        percentage = percentage, timeShiftHours = timeShiftHours, durationMinutes = durationMinutes,
        profileName = profileName, notes = notes ?: ""
    )

    is BatchAction.RunningMode         -> BatchActionDto(
        type = BatchActionDto.TYPE_RUNNING_MODE,
        runningMode = mode.name, durationMinutes = durationMinutes
    )

    is BatchAction.TempBasal           -> BatchActionDto(
        type = BatchActionDto.TYPE_TEMP_BASAL,
        rate = rate, isPercent = isPercent, durationMinutes = durationMinutes
    )

    is BatchAction.ExtendedBolus       -> BatchActionDto(
        type = BatchActionDto.TYPE_EXTENDED_BOLUS,
        insulin = insulin, durationMinutes = durationMinutes
    )

    is BatchAction.CancelTempBasal     -> BatchActionDto(type = BatchActionDto.TYPE_CANCEL_TEMP_BASAL)
    is BatchAction.CancelExtendedBolus -> BatchActionDto(type = BatchActionDto.TYPE_CANCEL_EXTENDED_BOLUS)

    is BatchAction.InsulinActivate     -> BatchActionDto(type = BatchActionDto.TYPE_INSULIN_ACTIVATE, iCfgJson = iCfg.toJsonObject().toString())

    is BatchAction.TherapyEvent        -> BatchActionDto(
        type = BatchActionDto.TYPE_THERAPY_EVENT,
        teType = teType.name, timestamp = timestamp, glucoseMgdl = glucoseMgdl, meterType = glucoseType?.name,
        durationMinutes = durationMinutes, notes = note ?: "", location = location?.name, arrow = arrow?.name, source = source.name
    )

    is BatchAction.TherapyEventEdit    -> BatchActionDto(
        type = BatchActionDto.TYPE_THERAPY_EVENT_EDIT,
        teType = teType.name, timestamp = timestamp, notes = note ?: "", location = location?.name, arrow = arrow?.name, source = source.name
    )
}

/** Wire [BatchActionDto] → domain [BatchAction] (master receive side); null if the type is unknown. */
internal fun BatchActionDto.toDomain(): BatchAction? = when (type) {
    BatchActionDto.TYPE_BOLUS                 -> BatchAction.Bolus(
        insulin = insulin, carbs = carbs, carbsTimeOffsetMinutes = carbsTimeOffsetMinutes,
        carbsDurationHours = carbsDurationHours, recordOnly = recordOnly, notes = notes, timestamp = timestamp,
        iCfg = iCfgJson?.let { j -> runCatching { (Json.parseToJsonElement(j) as? JsonObject)?.let { ICfg.fromJsonObject(it) } }.getOrNull() },
        eCarbsGrams = eCarbsGrams, eCarbsDelayMinutes = eCarbsDelayMinutes, eCarbsDurationHours = eCarbsDurationHours,
        quickWizardGuid = quickWizardGuid
    )

    BatchActionDto.TYPE_TEMP_TARGET           -> reason?.let { BatchAction.TempTarget(it, lowMgdl, highMgdl, durationMinutes, startOffsetMinutes, notes.ifEmpty { null }) }
    BatchActionDto.TYPE_PROFILE_SWITCH        -> BatchAction.ProfileSwitch(percentage, timeShiftHours, durationMinutes, profileName, notes.ifEmpty { null })
    BatchActionDto.TYPE_RUNNING_MODE          -> runningMode?.let { name -> runCatching { RM.Mode.valueOf(name) }.getOrNull()?.let { BatchAction.RunningMode(it, durationMinutes) } }
    BatchActionDto.TYPE_TEMP_BASAL            -> BatchAction.TempBasal(rate, isPercent, durationMinutes)
    BatchActionDto.TYPE_EXTENDED_BOLUS        -> BatchAction.ExtendedBolus(insulin, durationMinutes)
    BatchActionDto.TYPE_CANCEL_TEMP_BASAL     -> BatchAction.CancelTempBasal
    BatchActionDto.TYPE_CANCEL_EXTENDED_BOLUS -> BatchAction.CancelExtendedBolus
    // null (unparseable iCfg) drops the action — prepareBatch's no-action guard then rejects an insulin-only batch.
    BatchActionDto.TYPE_INSULIN_ACTIVATE      -> iCfgJson?.let { j -> runCatching { (Json.parseToJsonElement(j) as? JsonObject)?.let { ICfg.fromJsonObject(it) } }.getOrNull() }?.let { BatchAction.InsulinActivate(it) }
    // Unknown/unparseable teType drops the action (the master's no-action guard then rejects an empty batch).
    BatchActionDto.TYPE_THERAPY_EVENT         -> teType?.let { runCatching { TE.Type.valueOf(it) }.getOrNull() }?.let { type ->
        BatchAction.TherapyEvent(
            teType = type, timestamp = timestamp, glucoseMgdl = glucoseMgdl,
            glucoseType = meterType?.let { runCatching { TE.MeterType.valueOf(it) }.getOrNull() },
            durationMinutes = durationMinutes, note = notes.ifEmpty { null },
            location = location?.let { runCatching { TE.Location.valueOf(it) }.getOrNull() },
            arrow = arrow?.let { runCatching { TE.Arrow.valueOf(it) }.getOrNull() },
            source = source?.let { runCatching { Sources.valueOf(it) }.getOrNull() } ?: Sources.NSClient
        )
    }
    // Unknown/unparseable teType drops the action (the master's no-action guard then rejects an empty batch).
    BatchActionDto.TYPE_THERAPY_EVENT_EDIT    -> teType?.let { runCatching { TE.Type.valueOf(it) }.getOrNull() }?.let { type ->
        BatchAction.TherapyEventEdit(
            teType = type, timestamp = timestamp, note = notes.ifEmpty { null },
            location = location?.let { runCatching { TE.Location.valueOf(it) }.getOrNull() },
            arrow = arrow?.let { runCatching { TE.Arrow.valueOf(it) }.getOrNull() },
            source = source?.let { runCatching { Sources.valueOf(it) }.getOrNull() } ?: Sources.NSClient
        )
    }

    else                                      -> null
}
