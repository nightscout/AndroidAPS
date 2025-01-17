package app.aaps.core.data.model

import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import java.util.TimeZone

data class UE(
    var id: Long = 0L,
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var action: Action,
    var source: Sources,
    var note: String,
    var values: List<ValueWithUnit>
)