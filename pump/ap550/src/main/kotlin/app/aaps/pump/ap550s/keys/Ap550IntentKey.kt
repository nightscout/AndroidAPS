package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.ap550s.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Clickable rows on the AP550 preference screen.
 *
 * The click handlers themselves are attached in the plugin with `withClick`, because an enum cannot
 * hold a lambda that reaches back into the plugin.
 */
enum class Ap550IntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.URL,
    /** Shown in an OK/Cancel dialog before the click handler runs. */
    override val confirmationMessage: TextRef? = null,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS
) : IntentPreferenceKey {

    /**
     * Forget the paired pump. The click handler lives in the plugin.
     *
     * Only shown while a pump is paired. The row re-evaluates this when its confirm dialog closes,
     * so the row disappears right after unpairing, without leaving the screen.
     */
    Unpair(
        key = "ap550_unpair",
        title = TextRef.AndroidRes(CoreUiR.string.pump_unpair),
        confirmationMessage = TextRef.AndroidRes(R.string.ap550_confirm_unpair_message),
        visibility = ElementVisibility { ctx -> ctx.preferences.get(Ap550StringNonKey.SerialNumber).isNotEmpty() }
    )
}