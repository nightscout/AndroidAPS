package app.aaps.core.objects.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.lenientString
import app.aaps.core.utils.lenientStringOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

fun PS.getCustomizedName(decimalFormatter: DecimalFormatter): String {
    var name: String = profileName
    if (Constants.LOCAL_PROFILE == name) {
        name = decimalFormatter.to2Decimal(ProfileSealed.PS(value = this, activePlugin = null).percentageBasalSum()) + "U "
    }
    if (timeshift != 0L || percentage != 100) {
        name += " ($percentage%"
        if (timeshift != 0L) name += "," + T.msecs(timeshift).hours() + "h"
        name += ")"
    }
    return name
}

/**
 * Convert a [SingleProfile] to a [PureProfile] for graph rendering, validation, or activation.
 *
 * Both types now hold the same block lists, so this is a field copy. It used to render the profile
 * to JSON and parse it straight back, which is why the editor paid for a full serialise/parse round
 * trip on every keystroke.
 *
 * Nullable only to keep the call sites unchanged — a [SingleProfile] always carries usable blocks,
 * so this never actually returns null.
 */
fun SingleProfile.toPureProfile(dateUtil: DateUtil): PureProfile? =
    PureProfile(
        basalBlocks = basal,
        isfBlocks = isf,
        icBlocks = ic,
        targetBlocks = target,
        glucoseUnit = if (mgdl) GlucoseUnit.MGDL else GlucoseUnit.MMOL,
        utcOffset = systemUtcOffsetAt(dateUtil.now())
    )

/**
 * Pure profile doesn't contain timestamp, percentage, timeshift, profileName
 *
 * Every read here is deliberately as forgiving as the `org.json` version was, because the rules
 * decide whether a profile is usable at all:
 *
 * - **units** is the dangerous one. Absent, or explicitly JSON null, falls back to [defaultUnits],
 *   and only a still-missing value REJECTS the profile. That rejection has to survive, because
 *   `GlucoseUnit.fromText` never throws - it answers MGDL for anything it does not recognise, so a
 *   null slipping through would read an mmol/L profile as mg/dL and put every target, ISF and
 *   correction out by a factor of 18.
 * - a schedule that is missing, is not an array, or cannot be read yields **null**, i.e. an invalid
 *   profile, never an exception.
 */
fun pureProfileFromJson(jsonObject: JsonObject, dateUtil: DateUtil, defaultUnits: String? = null): PureProfile? {
    try {
        val txtUnits = jsonObject.lenientStringOrNull("units") ?: defaultUnits ?: return null
        val units = GlucoseUnit.fromText(txtUnits)
        val iCfg = (jsonObject["iCfg"] as? JsonObject)?.let { ICfg.fromJsonObject(it) }
        // The offset AT THIS MOMENT, not the zone's standard offset. Taking `rawOffset` here is what
        // made a summer Prague profile claim +01:00 and then get named after some unrelated zone that
        // really is at +01:00 in July. `java.util.TimeZone.getTimeZone` quietly answered GMT for an id
        // it did not know, and kotlinx throws instead, so that fallback is kept explicitly.
        val zoneName = jsonObject.lenientString("timezone", "UTC")
        val zone = runCatching { TimeZone.of(zoneName) }.getOrDefault(TimeZone.UTC)
        val utcOffset = zone.offsetAt(Instant.fromEpochMilliseconds(dateUtil.now())).totalSeconds * 1000L

        val isfBlocks = blockFromJson(jsonObject["sens"] as? JsonArray, dateUtil) ?: return null
        val icBlocks = blockFromJson(jsonObject["carbratio"] as? JsonArray, dateUtil)
            ?: return null
        val basalBlocks = blockFromJson(jsonObject["basal"] as? JsonArray, dateUtil)
            ?: return null
        val targetBlocks = targetBlockFromJson(jsonObject["target_low"] as? JsonArray, jsonObject["target_high"] as? JsonArray, dateUtil)
            ?: return null

        return PureProfile(
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            icBlocks = icBlocks,
            targetBlocks = targetBlocks,
            glucoseUnit = units,
            utcOffset = utcOffset,
            iCfg = iCfg
        )
    } catch (_: Exception) {
        return null
    }
}
