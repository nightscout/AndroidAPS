package info.nightscout.plugins.sync.nsclientV3.extensions

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.shared.utils.T

fun NSBolusWizard.toBolusCalculatorResult(): BolusCalculatorResult? =
    try {
        Gson().fromJson(bolusCalculatorResult, BolusCalculatorResult::class.java)
            .also {
                it.id = 0
                it.isValid = isValid
                it.interfaceIDs.nightscoutId = identifier
                it.version = 0
            }
    } catch (e: JsonSyntaxException) {
        null
    }

fun BolusCalculatorResult.toNSBolusWizard(): NSBolusWizard =
    NSBolusWizard(
        eventType = EventType.BOLUS_WIZARD,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        notes = note,
        bolusCalculatorResult = Gson().toJson(this).toString(),
        units = NsUnits.MG_DL,
        glucose = glucoseValue,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )