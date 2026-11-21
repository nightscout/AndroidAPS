package app.aaps.plugins.eversense.models

import app.aaps.plugins.eversense.enums.EversenseAlarm
import kotlinx.serialization.Serializable

@Serializable
data class ActiveAlarm(
    val code: EversenseAlarm,
    val codeRaw: Int,
    val flag: Int,
    val priority: Int
)
