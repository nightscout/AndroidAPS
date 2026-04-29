package app.aaps.core.interfaces.rx.events

/**
 * Posted by [AdaptiveSmoothingPlugin] to expose a lightweight, UI-friendly estimate of
 * "how much to trust" the current adaptive smoothing output.
 *
 * This is informational only in phase 1 (no control logic change).
 */
enum class AdaptiveSmoothingQualityTier {
    OK,
    UNCERTAIN,
    BAD
}

/**
 * Latest quality estimate from adaptive smoothing; safe to read on UI thread after [app.aaps.core.interfaces.smoothing.Smoothing.smooth].
 */
data class AdaptiveSmoothingQualitySnapshot(
    val tier: AdaptiveSmoothingQualityTier,
    val learnedR: Double,
    val outlierRate: Double,
    val compressionRate: Double,
    val updatedAtMillis: Long
)

class EventAdaptiveSmoothingQuality(
    val tier: AdaptiveSmoothingQualityTier,
    val learnedR: Double,
    val outlierRate: Double,
    val compressionRate: Double
) : Event()

