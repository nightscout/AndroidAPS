package app.aaps.core.data.db

import java.util.TimeZone

data class GV(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    var interfaceIDs_backing: InterfaceIDs? = InterfaceIDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var raw: Double?,
    var value: Double,
    var trendArrow: TrendArrow,
    var noise: Double?,
    var sourceSensor: SourceSensor
)