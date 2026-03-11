package app.aaps.plugins.aps.betacell

/**
 * BetaCellPrefs — Snapshot immuable de TOUS les paramètres SP du cycle
 *
 * Créé par BetaCellPlugin.prefs() en début de chaque invoke().
 * Garantit la cohérence du calcul si l'utilisateur modifie une préférence
 * en cours de cycle.
 *
 * Correspondance complète SP / XML :
 * ─────────────────────────────────────────────────────────────────────────
 *  Champ            Clé SP                          XML key                    Défaut
 * ─────────────────────────────────────────────────────────────────────────
 *  targetBg         betacell_target_bg              EditTextPreference          110.0
 *  hypoBg           betacell_hypo                   EditTextPreference           70.0  ← GARDE
 *  hyperBg          betacell_hyper                  EditTextPreference          180.0
 *  basalPhysio      betacell_basal_physio            EditTextPreference            3.7
 *  hepatic          betacell_hepatic_extraction      EditTextPreference            0.50
 *  iobTauMin        betacell_iob_tau                EditTextPreference           90.0
 *  isfMin           betacell_isf_min                EditTextPreference           25.0
 *  isfMax           betacell_isf_max                EditTextPreference           70.0
 *  isfWindowH       betacell_isf_window_hours       EditTextPreference              8
 *  slopeBrakeT      betacell_slope_brake_threshold  EditTextPreference           -0.5
 *  slopeBrakeF      betacell_slope_brake_factor     EditTextPreference            0.4
 *  smbEnabled       betacell_smb_enabled            SwitchPreference            true
 *  smbMax           betacell_smb_max_units          EditTextPreference            0.4
 *  smbOffset        betacell_smb_threshold_offset   EditTextPreference           20.0
 *  openLoopOnly     betacell_open_loop_only         SwitchPreference            true
 *  debugMode        betacell_debug_mode             SwitchPreference           false
 * ─────────────────────────────────────────────────────────────────────────
 *
 * Toutes les valeurs sont validées (coerceIn) dans BetaCellPlugin.prefs()
 * avant d'être stockées ici — ce data class est donc toujours dans des
 * bornes cliniquement sûres.
 */
data class BetaCellPrefs(

    // ── Cibles glycémiques (mg/dL) ────────────────────────────────────────
    val targetBg    : Double,   // betacell_target_bg          [70–160]
    val hypoBg      : Double,   // betacell_hypo               plancher 55 absolu ← GARDE
    val hyperBg     : Double,   // betacell_hyper              [140–300]

    // ── Modèle β-cell ─────────────────────────────────────────────────────
    val basalPhysio : Double,   // betacell_basal_physio       [0.5–8.0] U/h
    val hepatic     : Double,   // betacell_hepatic_extraction [0.0–0.8]
    val iobTauMin   : Double,   // betacell_iob_tau            [30–240] min

    // ── Calibration ISF rolling ───────────────────────────────────────────
    val isfMin      : Double,   // betacell_isf_min            [10–50]  mg/dL/U
    val isfMax      : Double,   // betacell_isf_max            [30–120] mg/dL/U
    val isfWindowH  : Int,      // betacell_isf_window_hours   [2–24]   heures

    // ── Frein pente descendante ───────────────────────────────────────────
    val slopeBrakeT : Double,   // betacell_slope_brake_threshold  [-3.0 – -0.1] mg/dL/min
    val slopeBrakeF : Double,   // betacell_slope_brake_factor      [0.1 – 1.0]

    // ── SMB ───────────────────────────────────────────────────────────────
    val smbEnabled  : Boolean,  // betacell_smb_enabled
    val smbMax      : Double,   // betacell_smb_max_units      [0.05–1.0] U
    val smbOffset   : Double,   // betacell_smb_threshold_offset [5–60] mg/dL

    // ── Mode ──────────────────────────────────────────────────────────────
    val openLoopOnly: Boolean,  // betacell_open_loop_only     (true = simulation)
    val debugMode   : Boolean,  // betacell_debug_mode
    val hypoAlertMargin : Double,  // betacell_hypo_alert_margin  defaut: 20.0 mg/dL
    val hypoRapidSlope  : Double   // betacell_hypo_rapid_slope   defaut: -2.0 mg/dL/min

) {
    /** Résumé lisible pour les logs AAPS (mode debug) */
    override fun toString(): String = buildString {
        append("tgt=$targetBg hypo=$hypoBg hyper=$hyperBg | ")
        append("basal=${basalPhysio}U/h hepatic=$hepatic τ=${iobTauMin}m | ")
        append("ISF[$isfMin–$isfMax] ${isfWindowH}h | ")
        append("brake@${slopeBrakeT}×$slopeBrakeF | ")
        append("SMB=$smbEnabled max=${smbMax}U +${smbOffset}mg/dL | ")
        append(if (openLoopOnly) "OPEN_LOOP" else "CLOSED_LOOP")
    }
}
