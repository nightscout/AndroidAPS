package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

/**
 * Legacy `IntentKey` enum. It is EMPTY and must stay empty.
 *
 * An intent key belongs to the module that owns the intent - `ApsIntentKey` in `:plugins:aps`,
 * `SmsIntentKey` and `XdripIntentKey` in `:plugins:sync`, `DanaIntentKey` and `DiaconnIntentKey` in
 * the pump drivers. Add new ones there, not here. The only reason this type still exists is that
 * `PreferencesImpl` registers `IntentKey.entries` on both phone and wear; it contributes no keys.
 *
 * The list that used to be here named `SmsIntentKey` in `:plugins:main` (it lives in
 * `:plugins:sync`) and an `OverviewIntentKey` that is in no module at all - it appears nowhere in
 * the repository except in that line. A list of where things live rots and then misleads; the rule
 * above does not, so keep the rule and do not grow it back into an inventory.
 */
enum class IntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    // This enum has no constants (see the note above), but the interface still requires the
    // properties. The `;` is what separates the - empty - constant list from the members.
    ;

}
