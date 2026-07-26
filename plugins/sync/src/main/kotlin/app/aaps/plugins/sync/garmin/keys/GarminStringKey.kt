package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.plugins.sync.R

enum class GarminStringKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int = 0,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true,
    override val validator: StringValidator = StringValidator.NONE
) : StringPreferenceKey {

    RequestKey(key = "garmin_aaps_key", defaultValue = "", titleResId = R.string.garmin_request_key),
}
