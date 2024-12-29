package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import com.google.gson.Gson

fun NSBolusWizard.toBolusCalculatorResult(): BCR? =
    try {
        Gson().fromJson(bolusCalculatorResult, BCR::class.java)
            .also {
                it.id = 0
                it.isValid = isValid
                it.ids.nightscoutId = identifier
                it.version = 0
            }
    } catch (e: Exception) {
        null
    }

fun BCR.toNSBolusWizard(): NSBolusWizard =
    NSBolusWizard(
        eventType = EventType.BOLUS_WIZARD,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        notes = note,
        bolusCalculatorResult = Gson().toJson(this).toString(),
        units = NsUnits.MG_DL,
        glucose = glucoseValue,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )