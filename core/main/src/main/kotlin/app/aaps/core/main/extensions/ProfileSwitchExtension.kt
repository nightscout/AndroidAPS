package app.aaps.core.main.extensions

import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import app.aaps.database.entities.ProfileSwitch
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