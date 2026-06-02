package app.aaps.plugins.eversense.models

import app.aaps.plugins.eversense.enums.EversenseTrendArrow

data class EversenseCGMResult(
    val glucoseInMgDl: Int,
    val datetime: Long,
    val trend: EversenseTrendArrow,
    val sensorId: String = "",
    val rawResponseHex: String = ""
)
