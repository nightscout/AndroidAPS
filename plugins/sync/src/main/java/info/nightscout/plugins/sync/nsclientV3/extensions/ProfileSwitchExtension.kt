package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.fromConstant
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T

fun NSProfileSwitch.toProfileSwitch(activePlugin: ActivePlugin, dateUtil: DateUtil): ProfileSwitch? {
    val pureProfile =
        profileJson?.let { pureProfileFromJson(it, dateUtil) ?: return null }
            ?: activePlugin.activeProfileSource.profile?.getSpecificProfile(profileName) ?: return null

    val profileSealed = ProfileSealed.Pure(pureProfile)

    return ProfileSwitch(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = ProfileSwitch.GlucoseUnit.fromConstant(profileSealed.units),
        profileName = originalProfileName ?: profileName,
        timeshift = timeShift ?: 0,
        percentage = percentage ?: 100,
        duration = originalDuration ?: T.mins(duration ?: 0).msecs(),
        insulinConfiguration = profileSealed.insulinConfiguration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}