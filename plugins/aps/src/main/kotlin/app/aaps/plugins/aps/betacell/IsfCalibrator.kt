package app.aaps.plugins.aps.betacell

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * IsfCalibrator — Calibration automatique de l'ISF (Insulin Sensitivity Factor)
 *
 * Port exact du bloc PS :
 * ─────────────────────────────────────────────────────────────
 *   $minISF = 25 ; $maxISF = 70 ; $defaultISF = 50 ; $sdEpsilon = 5
 *   if ($sdBG -lt $sdEpsilon) { $InsulinSensitivity = $defaultISF }
 *   else {
 *     $rawISF = 180 / $sdBG
 *     if ([double]::IsInfinity($rawISF) -or [double]::IsNaN($rawISF)) { ... }
 *     else { $ISF = [math]::Max($minISF, [math]::Min($maxISF, $rawISF)) }
 *   }
 * ─────────────────────────────────────────────────────────────
 *
 * Principe clinique :
 *   Quand la glycémie varie peu (SD faible) → le patient est sensible → ISF haut.
 *   Quand la glycémie varie beaucoup (SD élevée) → résistance → ISF bas.
 *   La formule 180/SD capture empiriquement cette relation.
 */
class IsfCalibrator(
    private val aapsLogger: AAPSLogger
) {

    companion object {
        const val ISF_MIN     = 25.0   // mg/dL/U — patient résistant
        const val ISF_MAX     = 70.0   // mg/dL/U — patient très sensible
        const val ISF_DEFAULT = 50.0   // fallback si données insuffisantes
        const val SD_EPSILON  = 5.0    // seuil clinique minimal (évite div/0)
    }

    /**
     * Calcule l'ISF calibré à partir de l'historique glycémique rolling.
     *
     * @param glucoseValues  Liste des glycémies en mg/dL (rolling 8h, ~96 points à 5min)
     * @return ISF calibré, borné dans [ISF_MIN, ISF_MAX]
     */
    fun calibrate(glucoseValues: List<Double>): Double {
        if (glucoseValues.size < 5) {
            aapsLogger.warn(LTag.APS, "IsfCalibrator: insufficient data (${glucoseValues.size} pts) → default $ISF_DEFAULT")
            return ISF_DEFAULT
        }

        val sdBG = standardDeviation(glucoseValues)
        aapsLogger.debug(LTag.APS, "IsfCalibrator: SD(BG) = ${"%.2f".format(sdBG)} mg/dL (n=${glucoseValues.size})")

        // PS: if ($sdBG -lt $sdEpsilon) → glycémie trop stable, éviter division par zéro
        if (sdBG < SD_EPSILON) {
            aapsLogger.debug(LTag.APS, "IsfCalibrator: SD < ε ($SD_EPSILON) → default $ISF_DEFAULT")
            return ISF_DEFAULT
        }

        // PS: $rawISF = 180 / $sdBG
        val rawISF = 180.0 / sdBG

        // PS: anti-Infinity / NaN guard
        if (rawISF.isInfinite() || rawISF.isNaN()) {
            aapsLogger.warn(LTag.APS, "IsfCalibrator: rawISF is Inf/NaN → default $ISF_DEFAULT")
            return ISF_DEFAULT
        }

        // PS: [math]::Max($minISF, [math]::Min($maxISF, $rawISF))
        val clampedISF = rawISF.coerceIn(ISF_MIN, ISF_MAX)

        aapsLogger.info(LTag.APS, "IsfCalibrator: raw=${"%.1f".format(rawISF)} → clamped=${"%.1f".format(clampedISF)} mg/dL/U")
        return clampedISF
    }

    /**
     * Écart-type population (n) — équivalent PowerShell Measure-Object -StdDev
     *
     * Kotlin stdlib n'a pas de stddev natif sur List<Double> → implémenté ici.
     */
    fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }

    /**
     * Version avec fenêtre glissante explicite.
     * Utile pour tester sur une sous-liste sans la tronquer en dehors.
     *
     * @param glucoseValues  Toutes les glycémies disponibles
     * @param windowSize     Taille de la fenêtre rolling (défaut 96 = 8h)
     */
    fun calibrateRolling(glucoseValues: List<Double>, windowSize: Int = 96): Double =
        calibrate(glucoseValues.takeLast(windowSize))
}
