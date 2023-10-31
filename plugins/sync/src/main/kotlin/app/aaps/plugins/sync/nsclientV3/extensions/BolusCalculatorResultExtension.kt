package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.IDs
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

fun NSBolusWizard.toBolusCalculatorResult(): BCR? =
    try {
        Gson().fromJson(bolusCalculatorResult, BCR::class.java)
            .also {
                it.id = 0
                it.isValid = isValid
                it.ids = IDs().apply { nightscoutId = identifier }
                it.version = 0
            }
    } catch (e: JsonSyntaxException) {
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