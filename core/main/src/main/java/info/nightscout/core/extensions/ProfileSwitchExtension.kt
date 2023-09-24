package info.nightscout.core.extensions

import app.aaps.interfaces.configuration.Constants
import app.aaps.interfaces.db.GlucoseUnit
import app.aaps.interfaces.profile.PureProfile
import app.aaps.interfaces.utils.DateUtil
import app.aaps.interfaces.utils.DecimalFormatter
import app.aaps.interfaces.utils.T
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.core.utils.JsonHelper
import info.nightscout.database.entities.ProfileSwitch
import org.json.JSONObject
import java.util.TimeZone

fun ProfileSwitch.getCustomizedName(decimalFormatter: DecimalFormatter): String {
    var name: String = profileName
    if (Constants.LOCAL_PROFILE == name) {
        name = decimalFormatter.to2Decimal(ProfileSealed.PS(this).percentageBasalSum()) + "U "
    }
    if (timeshift != 0L || percentage != 100) {
        name += "($percentage%"
        if (timeshift != 0L) name += "," + T.msecs(timeshift).hours() + "h"
        name += ")"
    }
    return name
}

fun ProfileSwitch.GlucoseUnit.Companion.fromConstant(units: GlucoseUnit): ProfileSwitch.GlucoseUnit =
    if (units == GlucoseUnit.MGDL) ProfileSwitch.GlucoseUnit.MGDL
    else ProfileSwitch.GlucoseUnit.MMOL

/**
 * Pure profile doesn't contain timestamp, percentage, timeshift, profileName
 */
fun pureProfileFromJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): PureProfile? {
    try {
        val txtUnits = JsonHelper.safeGetStringAllowNull(jsonObject, "units", defaultUnits) ?: return null
        val units = GlucoseUnit.fromText(txtUnits)
        val dia = JsonHelper.safeGetDoubleAllowNull(jsonObject, "dia") ?: return null
        val timezone = TimeZone.getTimeZone(JsonHelper.safeGetString(jsonObject, "timezone", "UTC"))

        val isfBlocks = blockFromJsonArray(jsonObject.getJSONArray("sens"), dateUtil) ?: return null
        val icBlocks = blockFromJsonArray(jsonObject.getJSONArray("carbratio"), dateUtil)
            ?: return null
        val basalBlocks = blockFromJsonArray(jsonObject.getJSONArray("basal"), dateUtil)
            ?: return null
        val targetBlocks = targetBlockFromJsonArray(jsonObject.getJSONArray("target_low"), jsonObject.getJSONArray("target_high"), dateUtil)
            ?: return null

        return PureProfile(
            jsonObject = jsonObject,
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            icBlocks = icBlocks,
            targetBlocks = targetBlocks,
            glucoseUnit = units,
            timeZone = timezone,
            dia = dia
        )
    } catch (ignored: Exception) {
        return null
    }
}