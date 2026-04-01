package com.nightscout.eversense.models

import com.nightscout.eversense.enums.EversenseTrendArrow

data class EversenseCGMResult(
    val glucoseInMgDl: Int,
    val datetime: Long,
    val trend: EversenseTrendArrow
)
