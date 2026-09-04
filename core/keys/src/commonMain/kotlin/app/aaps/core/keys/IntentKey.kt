package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

/**
 * Legacy IntentKey enum - keys have been migrated to module-specific key enums:
 * - ApsIntentKey in :plugins:aps
 * - XdripIntentKey in :plugins:sync
 * - SmsIntentKey in :plugins:main
 * - OverviewIntentKey in :plugins:main
 *
 * This enum is kept for backwards compatibility but should remain empty.
 * New intent keys should be added to their respective module key enums.
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
