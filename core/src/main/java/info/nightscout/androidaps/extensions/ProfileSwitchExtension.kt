package info.nightscout.androidaps.utils.extensions

import info.nightscout.interfaces.Constants
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.entities.ProfileSwitch
import info.nightscout.androidaps.extensions.blockFromJsonArray
import info.nightscout.androidaps.extensions.targetBlockFromJsonArray
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.shared.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter.to2Decimal
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.shared.utils.T
import org.json.JSONObject
import java.util.TimeZone

fun List<ProfileSwitch>.isPSEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            if (event.duration == 0L) {
                //aapsLogger.debug(LTag.DATABASE, "Found ProfileSwitch event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString())
                return true
            }
        }
    }
    return false
}

fun ProfileSwitch.getCustomizedName(): String {
    var name: String = profileName
    if (Constants.LOCAL_PROFILE == name) {
        name = to2Decimal(ProfileSealed.PS(this).percentageBasalSum()) + "U "
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