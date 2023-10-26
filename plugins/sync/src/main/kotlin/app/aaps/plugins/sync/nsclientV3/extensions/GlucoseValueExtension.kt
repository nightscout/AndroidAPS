package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import java.security.InvalidParameterException

fun NSSgvV3.toGV(): GV {
    return GV(
        timestamp = date ?: throw InvalidParameterException(),
        value = sgv,
        noise = noise,
        raw = filtered,
        trendArrow = TrendArrow.fromString(direction?.nsName),
        ids = IDs(nightscoutId = identifier),
        sourceSensor = SourceSensor.fromString(device),
        isValid = isValid,
        utcOffset = T.mins(utcOffset ?: 0L).msecs()
    )
}

fun GV.toNSSvgV3(): NSSgvV3 =
    NSSgvV3(
        isValid = isValid,
        date = timestamp,
        utcOffset = T.msecs(utcOffset).mins(),
        filtered = raw,
        unfiltered = 0.0,
        sgv = value,
        units = NsUnits.MG_DL,
        direction = Direction.fromString(trendArrow.text),
        noise = noise,
        device = sourceSensor.text,
        identifier = ids.nightscoutId
    )
