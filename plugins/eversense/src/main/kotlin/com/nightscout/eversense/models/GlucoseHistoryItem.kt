package com.nightscout.eversense.models

import com.nightscout.eversense.enums.EversenseTrendArrow

data class GlucoseHistoryItem(
    val valueInMgDl: Int,
    val datetime: Long,
    val trend: EversenseTrendArrow
)
