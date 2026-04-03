package app.aaps.plugins.main.general.overview.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.plugins.main.R

enum class OverviewIntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    QuickWizardSettings(
        key = "overview_quickwizard_settings",
        titleResId = R.string.quickwizard_settings,
        preferenceType = PreferenceType.ACTIVITY
    ),
}
