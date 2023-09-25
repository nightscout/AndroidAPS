package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.pureProfileFromJson
import app.aaps.core.main.profile.ProfileSealed
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.database.entities.EffectiveProfileSwitch
import app.aaps.database.entities.embedments.InterfaceIDs
import info.nightscout.plugins.sync.nsclient.extensions.fromConstant
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

fun EffectiveProfileSwitch.toNSEffectiveProfileSwitch(dateUtil: DateUtil): NSEffectiveProfileSwitch =
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