package app.aaps.core.data.model

import java.util.TimeZone

data class DS(
    var id: Long = 0,
    var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var device: String? = null,
    var pump: String? = null,
    var enacted: String? = null,
    var suggested: String? = null,
    var iob: String? = null,
    var uploaderBattery: Int,
    var isCharging: Boolean?,
    var configuration: String? = null
)