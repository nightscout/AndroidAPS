package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import java.security.InvalidParameterException

fun NSEffectiveProfileSwitch.toEffectiveProfileSwitch(dateUtil: DateUtil): EPS? {
    val pureProfile = pureProfileFromJson(profileJson, dateUtil) ?: return null
    val profileSealed = ProfileSealed.Pure(value = pureProfile, activePlugin = null)

    return EPS(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        basalBlocks = profileSealed.basalBlocks,
        isfBlocks = profileSealed.isfBlocks,
        icBlocks = profileSealed.icBlocks,
        targetBlocks = profileSealed.targetBlocks,
        glucoseUnit = profileSealed.units,
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        iCfg = profileSealed.iCfg,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )
}

fun EPS.toNSEffectiveProfileSwitch(dateUtil: DateUtil): NSEffectiveProfileSwitch =
    NSEffectiveProfileSwitch(
        eventType = EventType.NOTE,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        profileJson = ProfileSealed.EPS(value = this, activePlugin = null).toPureNsJson(dateUtil),
        originalProfileName = originalProfileName,
        originalCustomizedName = originalCustomizedName,
        originalTimeshift = originalTimeshift,
        originalPercentage = originalPercentage,
        originalDuration = originalDuration,
        originalEnd = originalEnd,
        notes = originalCustomizedName,
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )