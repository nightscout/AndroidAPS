package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.fromDb
import app.aaps.core.main.extensions.toDb
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.data.db.SourceSensor
import app.aaps.data.db.TrendArrow
import app.aaps.database.entities.GlucoseValue
import app.aaps.database.transactions.TransactionGlucoseValue
import java.security.InvalidParameterException

fun NSSgvV3.toTransactionGlucoseValue(): TransactionGlucoseValue {
    return TransactionGlucoseValue(
        timestamp = date ?: throw InvalidParameterException(),
        value = sgv,
        noise = noise,
        raw = filtered,
        trendArrow = TrendArrow.fromString(direction?.nsName).toDb(),
        nightscoutId = identifier,
        sourceSensor = SourceSensor.fromString(device).toDb(),
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
        direction = Direction.fromString(trendArrow.fromDb().text),
        noise = noise,
        device = sourceSensor.fromDb().text,
        identifier = interfaceIDs.nightscoutId
    )
