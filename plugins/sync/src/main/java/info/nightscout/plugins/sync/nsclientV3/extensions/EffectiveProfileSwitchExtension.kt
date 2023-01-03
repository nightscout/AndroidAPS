package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSEffectiveProfileSwitch.toEffectiveProfileSwitch(dateUtil: DateUtil): EffectiveProfileSwitch? {
    val pureProfile = pureProfileFromJson(profileJson, dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(pureProfile)

    return EffectiveProfileSwitch(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
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

fun EffectiveProfileSwitch.toNSEffectiveProfileSwitch(dateUtil: DateUtil) : NSEffectiveProfileSwitch =
    NSEffectiveProfileSwitch(
        eventType = EventType.NOTE,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        profileJson = ProfileSealed.EPS(this).toPureNsJson(dateUtil),
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        notes = originalCustomizedName,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )