package app.aaps.pump.omnipod.eros.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.common.R as CommonR

enum class ErosBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
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

    BatteryChangeLogging("AAPS.Omnipod.enable_battery_change_logging", false, titleResId = R.string.omnipod_eros_preferences_battery_change_logging_enabled, dependency = RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel),
    ShowSuspendDeliveryButton("AAPS.Omnipod.suspend_delivery_button_enabled", false, titleResId = CommonR.string.omnipod_common_preferences_suspend_delivery_button_enabled),
    ShowPulseLogButton("AAPS.Omnipod.pulse_log_button_enabled", false, titleResId = R.string.omnipod_eros_preferences_pulse_log_button_enabled),
    ShowRileyLinkStatsButton("AAPS.Omnipod.rileylink_stats_button_enabled", false, titleResId = R.string.omnipod_eros_preferences_riley_link_stats_button_enabled),
    TimeChangeEnabled("AAPS.Omnipod.time_change_enabled", true, titleResId = CommonR.string.omnipod_common_preferences_time_change_enabled),
}