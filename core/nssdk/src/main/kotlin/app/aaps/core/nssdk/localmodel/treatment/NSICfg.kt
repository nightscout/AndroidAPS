package app.aaps.core.nssdk.localmodel.treatment

import kotlinx.serialization.Serializable

@Serializable
data class NSICfg(
    val insulinLabel: String,
    val insulinEndTime: Long,
    val insulinPeakTime: Long,
    val concentration: Double,
    /** Null in payloads from builds before the field existed - callers reconstruct from the peak. */
    val isInhaled: Boolean? = null
)