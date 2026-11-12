package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.ap550s.R

/**
 * Boolean settings shown on the AP550 preference screen.
 *
 * The stored key strings are copied from the old `pref_ap550.xml` so an existing install keeps its
 * values after the move to the preference system.
 */
enum class Ap550BooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int
) : BooleanPreferenceKey {

    /**
     * Show temp basals as a percentage of the base basal instead of U/h.
     *
     * The pump itself only understands percentages, so this only changes what AAPS sends and shows.
     * Default true because the pump model is a percent pump - see `PumpType.AP550S`.
     */
    BasalIsPercent("Visualize_basal_percent", true, titleResId = R.string.ap550_basal_is_percent_title),

    /** Ask the pump to warn when a low glucose value is in the near future. */
    UseHypoglycemiaRiskInform("use_hypoglycemia_risk_inform", false, titleResId = R.string.ap550_use_hypoglycemia_risk_inform_title),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}