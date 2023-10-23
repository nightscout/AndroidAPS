package app.aaps.database.transactions

import app.aaps.database.entities.GlucoseValue
import java.util.TimeZone

data class TransactionGlucoseValue(
    val timestamp: Long,
    val value: Double,
    val raw: Double?,
    val noise: Double?,
    val trendArrow: GlucoseValue.TrendArrow,
    val nightscoutId: String? = null,
    val sourceSensor: GlucoseValue.SourceSensor,
    val isValid: Boolean = true,
    val utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong()
)