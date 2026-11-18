package app.aaps.plugins.eversense.models

import app.aaps.plugins.eversense.enums.EversenseTrendArrow

@kotlinx.serialization.Serializable
data class EversenseCGMResult @JvmOverloads constructor(
    val glucoseInMgDl: Int,
    val datetime: Long,
    val trend: EversenseTrendArrow,
    val sensorId: String = "",
    val rawResponseHex: String = "",
    val recordNumber: Int? = null
)
