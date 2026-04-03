package app.aaps.plugins.aps.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.plugins.aps.R

enum class ApsIntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.URL,
    override val urlResId: Int? = null,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    LinkToDocs(
        key = "aps_link_to_docs",
        titleResId = R.string.openapsama_link_to_preference_json_doc_txt,
        preferenceType = PreferenceType.URL,
        urlResId = R.string.openapsama_link_to_preference_json_doc
    )
}
