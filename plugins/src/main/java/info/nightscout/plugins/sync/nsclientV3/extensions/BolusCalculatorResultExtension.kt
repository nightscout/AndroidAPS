package info.nightscout.plugins.sync.nsclientV3.extensions

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard

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
