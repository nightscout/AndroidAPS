package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.fromConstant
import info.nightscout.core.extensions.getCustomizedName
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
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