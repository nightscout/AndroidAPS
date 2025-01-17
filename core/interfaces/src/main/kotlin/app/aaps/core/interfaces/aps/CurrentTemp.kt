package app.aaps.core.interfaces.aps

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class CurrentTemp(
    var duration: Int,
    var rate: Double,
    var minutesrunning: Int?
)