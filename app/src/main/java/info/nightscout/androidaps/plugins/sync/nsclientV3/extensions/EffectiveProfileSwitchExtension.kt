package info.nightscout.androidaps.plugins.sync.nsclientV3.extensions

import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.shared.utils.DateUtil
import info.nightscout.androidaps.utils.extensions.pureProfileFromJson
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch

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