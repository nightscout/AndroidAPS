package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.PS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import java.security.InvalidParameterException

fun NSProfileSwitch.toProfileSwitch(activePlugin: ActivePlugin, dateUtil: DateUtil): PS? {
    val pureProfile =
        profileJson?.let { pureProfileFromJson(it, dateUtil) ?: return null }
            ?: activePlugin.activeProfileSource.profile?.getSpecificProfile(profile) ?: return null

    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)

    return PS(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        profileName = originalProfileName ?: profile,
        timeshift = timeShift ?: 0,
        percentage = percentage ?: 100,
        duration = duration ?: 0L,
        iCfg = profileSealed.iCfg,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}

fun PS.toNSProfileSwitch(dateUtil: DateUtil, decimalFormatter: DecimalFormatter): NSProfileSwitch {
    val unmodifiedCustomizedName = getCustomizedName(decimalFormatter)
    // ProfileSealed.PS doesn't provide unmodified json -> reset it
    val notCustomized = this.copy()
    notCustomized.timeshift = 0
    notCustomized.percentage = 100

    return NSProfileSwitch(
        eventType = EventType.PROFILE_SWITCH,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        timeShift = timeshift,
        percentage = percentage,
        duration = duration,
        profile = unmodifiedCustomizedName,
        originalProfileName = profileName,
        originalDuration = duration,
        profileJson = ProfileSealed.PS(value = notCustomized, activePlugin = null).toPureNsJson(dateUtil),
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )
}