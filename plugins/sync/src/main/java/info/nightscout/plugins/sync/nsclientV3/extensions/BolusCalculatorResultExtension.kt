package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import app.aaps.database.entities.BolusCalculatorResult
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

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