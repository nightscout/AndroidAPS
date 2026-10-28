package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.SourceStrings

enum class DexcomOnePlusBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = true,
    override val exportable: Boolean = false,
) : BooleanPreferenceKey {

    /**
     * Put a missing SENSOR_CHANGE back for the running ONE+ session.
     *
     * On by default: without the event, the overview and the calibration plugin fall back to the
     * previous sensor's age. Nothing is written while "create sensor change" is off.
     */
    RepairMissingSensorChange(
        key = "dexcom_oneplus_repair_missing_sensor_change",
        defaultValue = true,
        title = SourceStrings.dexcom_oneplus_repair_missing_sensor_change,
        summary = SourceStrings.dexcom_oneplus_repair_missing_sensor_change_summary,
        exportable = false,
    ),

    /**
     * Let the Status screen move the insertion time of the sensor that feeds the loop.
     *
     * On by default: the action is only offered on the Status screen and asks for a date first.
     * Invalidating SENSOR_CHANGE events rewrites the sensor start; calibration rows are never
     * removed.
     */
    CorrectSensorStart(
        key = "dexcom_oneplus_correct_sensor_start",
        defaultValue = true,
        title = SourceStrings.dexcom_oneplus_correct_sensor_start,
        summary = SourceStrings.dexcom_oneplus_correct_sensor_start_summary,
        exportable = false,
    ),

    /**
     * Replace the automatic session start with a SENSOR_CHANGE the user already logged.
     *
     * Done on every start and on the first accepted reading. That rolls the stored start back by
     * up to 24 hours. On by default: a sensor inserted (and logged) before it is paired, or soaked
     * before it is promoted, keeps its real age. Off: the plain automatic time is used.
     */
    AnchorSessionToManualSensorChange(
        key = "dexcom_oneplus_anchor_session_to_manual_sensor_change",
        defaultValue = true,
        title = SourceStrings.dexcom_oneplus_anchor_session_to_manual_sensor_change,
        summary = SourceStrings.dexcom_oneplus_anchor_session_to_manual_sensor_change_summary,
        exportable = false,
    ),

    /**
     * Switches the pre-soak sensor to production by itself, [DexcomOnePlusIntKey.AutoPromoteHours]
     * after the pre-soak start, when its readings are recent and real. The promote button still works.
     *
     * Off by default: the switch stops the sensor in use, and the rest of its life is lost.
     */
    AutoPromote(
        key = "dexcom_oneplus_staging_auto_promote",
        defaultValue = false,
        title = SourceStrings.staging_auto_promote_title,
        summary = SourceStrings.staging_auto_promote_summary,
    ),
}
