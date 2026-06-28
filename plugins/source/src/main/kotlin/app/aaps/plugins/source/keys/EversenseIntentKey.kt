package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.plugins.source.R

enum class EversenseIntentKey(
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
    EversenseStatus(
        key = "eversense_status",
        titleResId = R.string.eversense_status_title
    ),
    EversenseCalibration(
        key = "eversense_calibration_launch",
        titleResId = R.string.eversense_calibration_action
    ),
    EversensePlacement(
        key = "eversense_placement_launch",
        titleResId = R.string.eversense_placement_title
    ),
    EversenseSignOut(
        key = "eversense_sign_out",
        titleResId = R.string.eversense_sign_out,
        preferenceType = PreferenceType.CLICK
    )
}




