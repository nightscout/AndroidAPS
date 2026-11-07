package app.aaps.core.data.model

import app.aaps.core.data.time.systemUtcOffsetAt

data class DS(
    var id: Long = 0,
    var ids: IDs = IDs(),
    override var timestamp: Long,
    var utcOffset: Long = systemUtcOffsetAt(timestamp),
    var device: String? = null,
    var pump: String? = null,
    var enacted: String? = null,
    var suggested: String? = null,
    var iob: String? = null,
    var uploaderBattery: Int,
    var isCharging: Boolean?,
    var configuration: String? = null
) : TimeStamped