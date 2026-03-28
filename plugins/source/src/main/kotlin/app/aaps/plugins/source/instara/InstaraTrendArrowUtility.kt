package app.aaps.plugins.source.instara

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer

/**
 * Instara trend arrow resolver.
 *
 * Logic:
 * 1) If Instara sends a valid arrow and it is not NONE -> use it directly.
 * 2) Otherwise (NONE or invalid):
 *    2.1 Try to calculate from previous sgvId (currentSgvId - 1) using glucose values:
 *        Let X = N1 - N0 (mmol/L)
 *        -0.22 < X < 0.22 -> Flat
 *        0.22 <= X <= 0.44 -> FortyFiveUp
 *        X > 0.44 -> SingleUp
 *        -0.44 <= X <= -0.22 -> FortyFiveDown
 *        X < -0.44 -> SingleDown
 *    2.2 If cannot calculate (previous sgvId not found OR previous value missing), fallback Flat.
 */
object InstaraTrendArrowResolver {

    /**
     * Resolve the TrendArrow for one Instara record.
     *
     * @param instaraDirectionRaw direction string from Instara JSON (may be missing/invalid/NONE).
     * @param currentValueMgdl current glucose value in mg/dL (GV.value).
     * @param currentSgvId 13-digit sgvId stored as pumpId.
     */
    suspend fun resolve( // <-- changed: suspend
        persistenceLayer: PersistenceLayer,
        instaraDirectionRaw: String?,
        currentValueMgdl: Double,
        currentSgvId: Long
    ): TrendArrow {
        // 1) If Instara provides a valid arrow and it is not NONE, use it.
        val fromInstara = TrendArrow.fromString(instaraDirectionRaw)
        if (fromInstara != TrendArrow.NONE) return fromInstara

        // 2) Instara provides NONE/invalid -> compute from previous sgvId using DB.
        val prevId = currentSgvId - 1
        if (prevId <= 0L) return TrendArrow.FLAT

        // Query previous value from DB (mg/dL). If missing -> fallback Flat.
        val prevValueMgdl: Double? = try {
            persistenceLayer.getInstaraValueMgdlByPumpId(prevId)
        } catch (_: Throwable) {
            null
        }

        if (prevValueMgdl == null) {
            // 2.2 cannot calculate -> Flat
            return TrendArrow.FLAT
        }

        val n1Mmol = currentValueMgdl / Constants.MMOLL_TO_MGDL
        val n0Mmol = prevValueMgdl / Constants.MMOLL_TO_MGDL
        val x = n1Mmol - n0Mmol

        // 2.1 Try to calculate from previous sgvId (currentSgvId - 1) using glucose values.
        return when {
            x > 0.44   -> TrendArrow.SINGLE_UP
            x >= 0.22  -> TrendArrow.FORTY_FIVE_UP
            x < -0.44  -> TrendArrow.SINGLE_DOWN
            x <= -0.22 -> TrendArrow.FORTY_FIVE_DOWN
            else       -> TrendArrow.FLAT
        }
    }
}