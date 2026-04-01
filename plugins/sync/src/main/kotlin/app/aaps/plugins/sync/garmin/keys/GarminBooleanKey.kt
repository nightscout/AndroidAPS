package app.aaps.plugins.sync.garmin.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.plugins.sync.R

enum class GarminBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    LocalHttpServer("communication_http", false, titleResId = R.string.garmin_local_http_server, defaultedBySM = true, hideParentScreenIfHidden = true),
}
