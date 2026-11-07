package app.aaps.plugins.source.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.plugins.source.R
import app.aaps.core.keys.interfaces.TextRef

enum class EversenseIntentKey(
    override val key: String,
    val titleResId: Int,
    val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
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
    ),
    EversenseDocumentation(
        key = "eversense_documentation",
        titleResId = R.string.eversense_documentation,
        preferenceType = PreferenceType.CLICK
    );

    override val confirmationMessage get() = if (this == EversenseSignOut)
        TextRef.AndroidRes(R.string.eversense_sign_out_confirmation) else null

    override val title get() = TextRef.AndroidRes(titleResId)
    override val summary get() = summaryResId?.let { TextRef.AndroidRes(it) }

}




