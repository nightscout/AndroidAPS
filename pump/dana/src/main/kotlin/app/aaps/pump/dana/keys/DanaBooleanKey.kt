package app.aaps.pump.dana.keys

import app.aaps.core.keys.BooleanPreferenceKey

enum class DanaBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : BooleanPreferenceKey {

    DanaRUseExtended("danar_useextended", true, defaultedBySM = true),
    DanaRsLogCannulaChange("rs_logcanulachange", true),
    DanaRsLogInsulinChange("rs_loginsulinchange", true),
}
