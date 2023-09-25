package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.transactions.TransactionGlucoseValue
import java.security.InvalidParameterException

fun NSSgvV3.toTransactionGlucoseValue(): TransactionGlucoseValue {
    return TransactionGlucoseValue(
        timestamp = date ?: throw InvalidParameterException(),
        value = sgv,
        noise = noise,
        raw = filtered,
        trendArrow = GlucoseValue.TrendArrow.fromString(direction?.nsName),
        nightscoutId = identifier,
        sourceSensor = GlucoseValue.SourceSensor.fromString(device),
        isValid = isValid,
        utcOffset = T.mins(utcOffset ?: 0L).msecs()
    )
}

// for testing
fun TransactionGlucoseValue.toGlucoseValue() =
    GlucoseValue(
        timestamp = timestamp,
        raw = raw,
        value = value,
        noise = noise,
        trendArrow = trendArrow,
        sourceSensor = sourceSensor,
        isValid = isValid,
        utcOffset = utcOffset
    ).also { gv ->
        gv.interfaceIDs.nightscoutId = nightscoutId
    }

fun GlucoseValue.toNSSvgV3(): NSSgvV3 =
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
        identifier = interfaceIDs.nightscoutId
    )
