package app.aaps.pump.omnipod.eros.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey

enum class ErosBooleanPreferenceKey(
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

    BatteryChangeLogging("AAPS.Omnipod.enable_battery_change_logging", false, dependency = RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel),
    ShowSuspendDeliveryButton("AAPS.Omnipod.suspend_delivery_button_enabled", false),
    ShowPulseLogButton("AAPS.Omnipod.pulse_log_button_enabled", false),
    ShowRileyLinkStatsButton("AAPS.Omnipod.rileylink_stats_button_enabled", false),
    TimeChangeEnabled("AAPS.Omnipod.time_change_enabled", true),
}