package app.aaps.core.interfaces.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Which logical sensor a reading belongs to when a CGM source can overlap a new sensor
 * over the outgoing one (pre-soak / staging).
 *
 * [PRODUCTION] feeds the loop. [STAGING] is warmed in parallel and is never published
 * until the user promotes it.
 */
enum class SensorSlot { PRODUCTION, STAGING }

/**
 * State of the optional STAGING slot, for a "new sensor" card.
 *
 * - [ABSENT] no staging sensor started.
 * - [WARMUP] staging sensor warming up / reconnecting (no glucose yet).
 * - [SETTLING] warmed up, collecting glucose, not settled long enough to promote.
 * - [READY] settled and producing valid glucose → promotion allowed.
 */
enum class StagingState { ABSENT, WARMUP, SETTLING, READY }

/**
 * Generic sensor life info so the UI can show the sensor age and the end of life (time to start a
 * new sensor) without naming a vendor.
 *
 * Times are in ms. Fields are null when the source cannot know them.
 */
data class CgmSensorLifecycle(
    val startedAtEpochMs: Long?,
    val ageMs: Long?,
    val endOfLife: Boolean,
)

/**
 * Proof that the collect-only staging sensor is really producing data.
 *
 * A pre-soak asks the user to wait many hours on a sensor whose readings are never
 * published. Without this, a working sensor and a dead one look the same.
 */
data class CgmStagingEvidence(
    val validCount: Int,
    val lastValueMgdl: Double?,
    val lastValueAtEpochMs: Long?,
)

/** Why a promote-staging-to-production request was refused. */
enum class PromotionRejectReason {
    STAGING_ABSENT,
    STAGING_NOT_SETTLED,
    STAGING_NO_VALID_GLUCOSE,

    /** Early promotion asked, but the staging sensor has not sent a recent reading. */
    STAGING_NO_RECENT_GLUCOSE,
}

/**
 * Result of a promote-staging-to-production request.
 *
 * [OkBoundFailed] and [OkFollowUpFailed] say that the sensors were swapped but a later step
 * failed. They are not a silent [Ok], so the user can be told.
 */
sealed interface PromotionResult {
    /** Promotion succeeded: the staging sensor now feeds the loop, and older calibration entries are ignored. */
    data object Ok : PromotionResult

    /**
     * The exchange already happened and is kept.
     * [app.aaps.core.interfaces.calibration.Calibration.ignoreEntriesBefore] threw, so older
     * calibration entries were not ignored. Not a rollback, and not a full success.
     */
    data object OkBoundFailed : PromotionResult

    /**
     * The exchange already happened, older calibration entries are ignored, and a later step
     * (loading or resuming the session) threw.
     * [productionIdentityPresent] is read from the production store after that throw.
     */
    data class OkFollowUpFailed(val productionIdentityPresent: Boolean) : PromotionResult

    /** Promotion refused; [reason] says why (no state changed). */
    data class Rejected(val reason: PromotionRejectReason) : PromotionResult
}

/**
 * Implemented by a [BgSource] that can overlap a second (staging) sensor over production.
 * Extends [CgmWarmupProvider]: production warm-up stays the source of truth for the warm-up display.
 *
 * The STAGING slot is collect-only until [promoteStagingToProduction].
 */
interface CgmSensorStatusProvider : CgmWarmupProvider {

    /** Production sensor lifecycle, or null when unknown / no sensor. */
    val lifecycle: StateFlow<CgmSensorLifecycle?>

    /** Staging sensor lifecycle, or null when there is no staging sensor. */
    val stagingLifecycle: StateFlow<CgmSensorLifecycle?>

    /** Coarse staging slot state for the "new sensor" card. */
    val stagingState: StateFlow<StagingState>

    /** Evidence that staging is collecting, or null when there is no staging sensor. */
    val stagingEvidence: StateFlow<CgmStagingEvidence?>

    /**
     * Milliseconds left before the staging sensor has settled long enough to be promoted, or null
     * when there is no staging sensor or the wait is already over.
     *
     * The source owns the settle rule, so the UI can show a countdown without knowing it. A UI that
     * kept its own copy of the threshold would drift from the rule that really gates promotion.
     */
    val stagingSettleRemainingMs: StateFlow<Long?>

    /**
     * Promote the staging sensor to production. This is the only action that changes
     * the loop glucose source.
     *
     * @param allowEarly the user promotes a sensor that has not finished its soak,
     *   because production stopped early. Soak is skipped, but evidence gates are not:
     *   staging must have enough valid readings and one recent reading.
     */
    suspend fun promoteStagingToProduction(allowEarly: Boolean = false): PromotionResult
}
