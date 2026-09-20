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
    val autosensTarget: String? = null,
    val defaultRange: TargetRange,
    val oapsResult: OapsResultInfo?,
    /** End time (epoch ms) of a temporary running mode (suspend/disconnect/superbolus), null when the mode is permanent */
    val modeEndTime: Long? = null,
    /** The active scene, null when none. Defaulted and last, so an older peer decodes without it. */
    val activeScene: ActiveSceneInfo? = null,
    /** The profile in force, null when none is set. Defaulted and last, like [activeScene]. */
    val profile: ProfileInfo? = null,
    /** Whether the active scene set the running mode, so the watch can mark it. Defaulted and last. */
    val modeFromScene: Boolean = false
) {

    @Serializable
    enum class LoopMode {

        CLOSED,
        OPEN,
        LGS,
        DISABLED,
        SUSPENDED,
        PUMP_SUSPENDED,
        DST_SUSPENDED,
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
    val units: String,
    /** Set by the active scene, so the watch can mark it as the scene's doing. Defaulted and last. */
    val fromScene: Boolean = false
)

/**
 * The profile in force, as the watch's Loop Status shows it.
 *
 * @param name the profile's own name, without the percentage and timeshift the phone folds into
 *   its customized name; the watch shows those as rows of their own
 * @param percentage 100 when unchanged
 * @param timeshiftHours 0 when unchanged
 * @param endTime when a temporary switch ends, epoch ms; null for a permanent one
 * @param returnsTo the profile that is in force again after [endTime], null for a permanent switch
 * @param fromScene whether the active scene made this switch
 */
@Serializable
data class ProfileInfo(
    val name: String,
    val percentage: Int,
    val timeshiftHours: Int,
    val endTime: Long?,
    val returnsTo: String?,
    val fromScene: Boolean = false
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

/**
 * The active scene as the watch's Loop Status shows it.
 *
 * @param name the scene's name
 * @param endTime when it ends, epoch ms; null for an indefinite scene
 * @param chainTargetName the follow-up scene that starts when it ends, null when none is
 *   configured or the phone cannot run it
 */
@Serializable
data class ActiveSceneInfo(
    val name: String,
    val endTime: Long?,
    val chainTargetName: String?
)
