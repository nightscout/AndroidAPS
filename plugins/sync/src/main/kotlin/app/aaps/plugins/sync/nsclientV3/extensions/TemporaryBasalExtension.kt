package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.objects.extensions.convertedToAbsolute
import java.security.InvalidParameterException

fun NSTemporaryBasal.toTemporaryBasal(): TB =
    TB(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        type = type.toType(),
        rate = rate,
        isAbsolute = isAbsolute,
        duration = duration,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun NSTemporaryBasal.Type?.toType(): TB.Type =
    TB.Type.fromString(this?.name)

fun TB.toNSTemporaryBasal(profile: Profile): NSTemporaryBasal =
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
        identifier = ids.nightscoutId,
        pumpId = ids.pumpId,
        pumpType = ids.pumpType?.name,
        pumpSerial = ids.pumpSerial,
        endId = ids.endId
    )

fun TB.Type?.toType(): NSTemporaryBasal.Type =
    NSTemporaryBasal.Type.fromString(this?.name)
