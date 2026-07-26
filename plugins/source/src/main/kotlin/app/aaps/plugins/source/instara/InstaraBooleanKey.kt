package app.aaps.plugins.source.instara

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.plugins.source.R

// Instara plugin-local user-editable preference keys
enum class InstaraBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = true,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    HistoryRequestEnabled(
        key = "instara_history_request_setting",
        defaultValue = true,
        titleResId = R.string.pref_title_instara_history_request,
        summaryResId = R.string.pref_summary_instara_history_request,
        showInNsClientMode = false
    )
}