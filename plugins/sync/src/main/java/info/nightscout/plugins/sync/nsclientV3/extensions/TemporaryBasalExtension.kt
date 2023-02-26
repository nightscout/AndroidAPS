package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.profile.Profile
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.shared.utils.T
import java.security.InvalidParameterException

fun NSTemporaryBasal.toTemporaryBasal(): TemporaryBasal =
    TemporaryBasal(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        type = type.toType(),
        rate = rate,
        isAbsolute = isAbsolute,
        duration = duration,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSTemporaryBasal.Type?.toType(): TemporaryBasal.Type =
    TemporaryBasal.Type.fromString(this?.name)

fun TemporaryBasal.toNSTemporaryBasal(profile: Profile): NSTemporaryBasal =
    NSTemporaryBasal(
        eventType = EventType.TEMPORARY_BASAL,
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        type = type.toType(),
        rate = convertedToAbsolute(timestamp, profile),
        isAbsolute = isAbsolute,
        absolute = if (isAbsolute) rate else null,
        percent = if (!isAbsolute) rate - 100 else null,
        duration = duration,
        identifier = interfaceIDs.nightscoutId,
        pumpId = interfaceIDs.pumpId,
        pumpType = interfaceIDs.pumpType?.name,
        pumpSerial = interfaceIDs.pumpSerial,
        endId = interfaceIDs.endId
    )

fun TemporaryBasal.Type?.toType(): NSTemporaryBasal.Type =
    NSTemporaryBasal.Type.fromString(this?.name)
