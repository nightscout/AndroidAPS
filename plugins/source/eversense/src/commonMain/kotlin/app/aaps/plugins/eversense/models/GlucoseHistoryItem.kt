package app.aaps.plugins.eversense.models

import app.aaps.plugins.eversense.enums.EversenseTrendArrow

data class GlucoseHistoryItem(
    val valueInMgDl: Int,
    val datetime: Long,
    val trend: EversenseTrendArrow,
    val rawResponseHex: String = ""
)
