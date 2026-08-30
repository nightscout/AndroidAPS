package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import kotlinx.serialization.json.Json

/**
 * Reads and writes the bolus wizard breakdown that rides inside the Nightscout record.
 *
 * This used to be Gson. The settings below are what keep the text identical to what Gson wrote, and
 * they are not style choices - records written by older builds are still out there, and older builds
 * still read what this writes:
 *
 * - [Json.encodeDefaults] on, because Gson writes every field. Left off, a field sitting at its
 *   default would simply be missing, and Gson filling a missing `isValid` gives `false` - a valid
 *   record would come back invalid.
 * - [Json.explicitNulls] off, because Gson leaves nulls out rather than writing `"x": null`.
 * - [Json.ignoreUnknownKeys] on, so a record written by a newer build with an extra field still
 *   loads instead of throwing.
 *
 * `BolusCalculatorResultJsonCompatTest` holds this to it in both directions.
 */
internal val bcrJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

fun NSBolusWizard.toBolusCalculatorResult(): BCR? {
    val text = bolusCalculatorResult ?: return null
    return try {
        bcrJson.decodeFromString<BCR>(text)
            .also {
                it.id = 0
                it.isValid = isValid
                it.ids.nightscoutId = identifier
                it.version = 0
            }
    } catch (_: Exception) {
        null
    }
}

fun BCR.toNSBolusWizard(): NSBolusWizard =
    NSBolusWizard(
        eventType = EventType.BOLUS_WIZARD,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        notes = note,
        bolusCalculatorResult = bcrJson.encodeToString(this),
        units = NsUnits.MG_DL,
        glucose = glucoseValue,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )
