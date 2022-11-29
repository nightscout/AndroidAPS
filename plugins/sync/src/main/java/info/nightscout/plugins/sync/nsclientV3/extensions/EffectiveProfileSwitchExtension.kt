package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.shared.utils.DateUtil

fun NSEffectiveProfileSwitch.toEffectiveProfileSwitch(dateUtil: DateUtil): EffectiveProfileSwitch? {
    val pureProfile = pureProfileFromJson(profileJson, dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(pureProfile)

    return EffectiveProfileSwitch(
        isValid = isValid,
        timestamp = date,
        utcOffset = utcOffset,
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = EffectiveProfileSwitch.GlucoseUnit.fromConstant(profileSealed.units),
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        insulinConfiguration = profileSealed.insulinConfiguration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}