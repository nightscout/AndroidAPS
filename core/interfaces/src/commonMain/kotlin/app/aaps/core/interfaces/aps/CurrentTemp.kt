package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

@Serializable
data class CurrentTemp(
    var duration: Int,
    var rate: Double,
    var minutesrunning: Int?
)