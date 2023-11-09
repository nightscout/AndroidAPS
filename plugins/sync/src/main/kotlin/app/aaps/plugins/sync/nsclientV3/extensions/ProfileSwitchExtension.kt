package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.fromConstant
import app.aaps.core.main.extensions.getCustomizedName
import app.aaps.core.main.extensions.pureProfileFromJson
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.database.entities.ProfileSwitch
import app.aaps.database.entities.embedments.InterfaceIDs
import java.security.InvalidParameterException

fun NSProfileSwitch.toProfileSwitch(activePlugin: ActivePlugin, dateUtil: DateUtil): ProfileSwitch? {
    val pureProfile =
        profileJson?.let { pureProfileFromJson(it, dateUtil) ?: return null }
            ?: activePlugin.activeProfileSource.profile?.getSpecificProfile(profile) ?: return null

    val profileSealed = ProfileSealed.Pure(pureProfile)

    return ProfileSwitch(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(profileSealed.units),
        profileName = originalProfileName ?: profile,
        timeshift = timeShift ?: 0,
        percentage = percentage ?: 100,
        duration = originalDuration ?: T.mins(duration ?: 0).msecs(),
        insulinConfiguration = profileSealed.insulinConfiguration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}

fun ProfileSwitch.toNSProfileSwitch(dateUtil: DateUtil, decimalFormatter: DecimalFormatter): NSProfileSwitch {
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
        duration = T.mins(duration).msecs(),
        profile = unmodifiedCustomizedName,
        originalProfileName = profileName,
        originalDuration = duration,
        profileJson = ProfileSealed.PS(notCustomized).toPureNsJson(dateUtil),
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )
}