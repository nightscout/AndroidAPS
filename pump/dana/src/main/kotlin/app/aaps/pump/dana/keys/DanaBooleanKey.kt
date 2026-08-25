package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.dana.R

enum class DanaBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    UseExtended("danar_useextended", true, titleResId = R.string.danar_useextended_title, defaultedBySM = true),
    LogCannulaChange("rs_logcanulachange", true, titleResId = R.string.rs_logcanulachange_title, summaryResId = R.string.rs_logcanulachange_summary),
    LogInsulinChange("rs_loginsulinchange", true, titleResId = R.string.rs_loginsulinchange_title, summaryResId = R.string.rs_loginsulinchange_summary),
}
