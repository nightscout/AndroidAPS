package app.aaps.plugins.aps.betacell

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.exp

/**
 * IobCalculatorBeta — IOB non-linéaire β-cell (cinétique exponentielle)
 *
 * Port du bloc PS :
 * ─────────────────────────────────────────────────────────────
 *   $IOB_Tau = 90   # min
 *
 *   function Get-IOB {
 *     param($history, $now)
 *     $iob = 0
 *     foreach ($h in $history) {
 *       $dt = ($now - $h.Time).TotalMinutes
 *       if ($dt -ge 0) {
 *         $iob += $h.Dose * [math]::Exp(-$dt / $IOB_Tau)
 *       }
 *     }
 *     return $iob
 *   }
 * ─────────────────────────────────────────────────────────────
 *
 * Modèle : chaque dose livrée par le plugin β-cell se décroît
 * exponentiellement avec τ=90 min (demi-vie ~62 min).
 *
 * IMPORTANT : Ce calculateur est utilisé uniquement pour le suivi
 * de l'insuline délivrée par BetaCellPlugin lui-même (basal + SMB β-cell).
 * L'IOB des bolus repas reste géré par IobCobCalculator d'AAPS.
 */
class IobCalculatorBeta(
    private val aapsLogger: AAPSLogger
) {
    /**
     * τ = 90 min — cinétique non-linéaire β-cell.
     * Valeur tirée de la littérature pour insuline rapide voie SC.
     * Modifiable via préférences si besoin.
     */
    var tauMinutes: Double = IOB_TAU_DEFAULT

    companion object {
        const val IOB_TAU_DEFAULT = 90.0  // minutes — $IOB_Tau PS
    }

    /**
     * Entrée d'historique d'insuline β-cell.
     *
     * @param timestampMs  Horodatage de la livraison (epoch ms)
     * @param doseU        Dose livrée en Unités (insuline systémique post-hépatique)
     */
    data class InsulinEntry(
        val timestampMs: Long,
        val doseU: Double
    )

    /**
     * Calcule l'IOB β-cell total à l'instant [nowMs].
     *
     * PS équivalent :
     *   $iob += $h.Dose * [math]::Exp(-$dt / $IOB_Tau)
     *
     * @param history  Historique des doses β-cell livrées
     * @param nowMs    Instant courant (epoch ms)
     * @return IOB en Unités (double)
     */
    fun calculateIob(history: List<InsulinEntry>, nowMs: Long): Double {
        var iob = 0.0
        for (entry in history) {
            val dtMin = (nowMs - entry.timestampMs) / 60_000.0
            if (dtMin >= 0.0) {
                // PS: $h.Dose * [math]::Exp(-$dt / $IOB_Tau)
                iob += entry.doseU * exp(-dtMin / tauMinutes)
            }
        }
        aapsLogger.debug(LTag.APS, "IobCalculatorBeta: IOB=${"%.3f".format(iob)} U (τ=${tauMinutes}min, n=${history.size} entries)")
        return iob
    }

    /**
     * Indique si l'IOB est suffisamment élevé pour freiner la sécrétion.
     * Seuil heuristique : IOB > 2×basalPhysio×(tau/60)
     * (équivalent à ~3h de basal encore actif)
     */
    fun isHighIob(iob: Double, basalPhysioUh: Double): Boolean {
        val threshold = 2.0 * basalPhysioUh * (tauMinutes / 60.0)
        return iob > threshold
    }

    /**
     * Prédit le temps (en minutes) avant que l'IOB passe sous un seuil.
     * Utile pour le debug / affichage fragment.
     *
     * IOB(t) = IOB_now × e^(-t/τ) < threshold
     * → t = τ × ln(IOB_now / threshold)
     */
    fun minutesUntilIobBelow(currentIob: Double, threshold: Double): Double {
        if (currentIob <= threshold || currentIob <= 0.0) return 0.0
        return tauMinutes * Math.log(currentIob / threshold)
    }
}
