package app.aaps.core.interfaces.rx.weardata

import kotlinx.serialization.Serializable

@Serializable
data class LoopStatusData(
    val timestamp: Long,
    val loopMode: LoopMode,
    val apsName: String?,
    val lastRun: Long?,
    val lastEnact: Long?,
    val tempTarget: TempTargetInfo?,
    val defaultRange: TargetRange,
    val oapsResult: OapsResultInfo?
) {
    @Serializable
    enum class LoopMode {
        CLOSED,
        OPEN,
        LGS,
        DISABLED,
        SUSPENDED,
        DISCONNECTED,
        SUPERBOLUS,
        UNKNOWN
    }
}

@Serializable
data class TempTargetInfo(
    val targetDisplay: String,
    val endTime: Long,
    val durationMinutes: Int,
    val units: String
)

@Serializable
data class TargetRange(
    val lowDisplay: String,
    val highDisplay: String,
    val targetDisplay: String,
    val units: String
)

@Serializable
data class OapsResultInfo(
    val changeRequested: Boolean,
    val isLetTempRun: Boolean = false,
    val rate: Double?,
    val ratePercent: Int?,
    val duration: Int?,
    val reason: String,
    val smbAmount: Double? = null
)