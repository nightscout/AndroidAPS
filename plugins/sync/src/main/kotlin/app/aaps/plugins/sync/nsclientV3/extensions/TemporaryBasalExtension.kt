package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.convertedToAbsolute
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.database.entities.TemporaryBasal
import app.aaps.database.entities.embedments.InterfaceIDs
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
