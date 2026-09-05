package app.aaps.core.data.model

import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

data class UE(
    var id: Long = 0L,
    override var timestamp: Long,
    var utcOffset: Long = systemUtcOffsetAt(timestamp),
    var action: Action,
    var source: Sources,
    var note: String,
    var values: List<ValueWithUnit>
) : TimeStamped