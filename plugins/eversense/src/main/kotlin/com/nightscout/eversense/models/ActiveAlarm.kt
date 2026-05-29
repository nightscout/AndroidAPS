package com.nightscout.eversense.models

import com.nightscout.eversense.enums.EversenseAlarm
import kotlinx.serialization.Serializable

@Serializable
data class ActiveAlarm(
    val code: EversenseAlarm,
    val codeRaw: Int,
    val flag: Int,
    val priority: Int
)
