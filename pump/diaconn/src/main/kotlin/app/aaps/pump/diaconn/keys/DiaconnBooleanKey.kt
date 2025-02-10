package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class DiaconnBooleanKey(
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
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    LogInsulinChange("diaconn_g8_loginsulinchange", true),
    LogCannulaChange("diaconn_g8_logneedlechange", true),
    LogTubeChange("diaconn_g8_logtubechange", true),
    LogBatteryChange("diaconn_g8_logbatterychanges", true),
    SendLogsToCloud("diaconn_g8_cloudsend", true),
}
