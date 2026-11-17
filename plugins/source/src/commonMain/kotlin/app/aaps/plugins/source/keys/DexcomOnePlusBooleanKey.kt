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
     * Send a fingerstick to the sensor instead of correcting its readings inside the phone.
     *
     * On by default: a fingerstick is the reference, so it goes to the sensor's own algorithm, as
     * the Dexcom app does. A sensor keeps a calibration it accepts for good — it cannot be edited
     * or deleted — so the confirmation says so, and `OnePlusCalibrationGate` refuses a value at a
     * bad moment (warm-up, no recent reading, glucose changing fast, blood older than one hour).
     * While it is on, the app does not fit its own calibration line on this source's readings.
     * Off: the value is stored as an app calibration entry, as for any other source.
     */
    SendCalibrationToSensor(
        key = "dexcom_oneplus_send_calibration_to_sensor",
        defaultValue = true,
        title = SourceStrings.dexcom_oneplus_send_calibration_to_sensor,
        summary = SourceStrings.dexcom_oneplus_send_calibration_to_sensor_summary,
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

    /**
     * Direct-to-watch: a Wear OS watch also reads the sensor that feeds the loop, in the sensor's
     * watch slot, so the watch faces keep a value when the phone is away. The watch's readings stay
     * on the watch and never reach the loop.
     *
     * Off by default. Offered only where the phone has a watch link (Wear OS).
     */
    WatchDirect(
        key = "dexcom_oneplus_watch_direct",
        defaultValue = false,
        title = SourceStrings.dexcom_oneplus_watch_direct,
        summary = SourceStrings.dexcom_oneplus_watch_direct_summary,
    ),
}
