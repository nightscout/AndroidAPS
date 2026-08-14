package app.aaps.core.objects.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import org.json.JSONObject
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
 */
fun pureProfileFromJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): PureProfile? {
    try {
        val txtUnits = JsonHelper.safeGetStringAllowNull(jsonObject, "units", defaultUnits) ?: return null
        val units = GlucoseUnit.fromText(txtUnits)
        val iCfg = JsonHelper.safeGetJSONObject(jsonObject, "iCfg", null)?.let {
            ICfg.fromJson(it)
        }
        // The offset AT THIS MOMENT, not the zone's standard offset. Taking `rawOffset` here is what
        // made a summer Prague profile claim +01:00 and then get named after some unrelated zone that
        // really is at +01:00 in July. `java.util.TimeZone.getTimeZone` quietly answered GMT for an id
        // it did not know, and kotlinx throws instead, so that fallback is kept explicitly.
        val zoneName = JsonHelper.safeGetString(jsonObject, "timezone", "UTC")
        val zone = runCatching { TimeZone.of(zoneName) }.getOrDefault(TimeZone.UTC)
        val utcOffset = zone.offsetAt(Instant.fromEpochMilliseconds(dateUtil.now())).totalSeconds * 1000L

        val isfBlocks = blockFromJsonArray(jsonObject.getJSONArray("sens"), dateUtil) ?: return null
        val icBlocks = blockFromJsonArray(jsonObject.getJSONArray("carbratio"), dateUtil)
            ?: return null
        val basalBlocks = blockFromJsonArray(jsonObject.getJSONArray("basal"), dateUtil)
            ?: return null
        val targetBlocks = targetBlockFromJsonArray(jsonObject.getJSONArray("target_low"), jsonObject.getJSONArray("target_high"), dateUtil)
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