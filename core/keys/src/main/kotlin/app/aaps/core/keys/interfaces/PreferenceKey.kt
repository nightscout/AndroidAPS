package app.aaps.core.keys.interfaces

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.UnitType

/**
 * Marker interface for items that can appear in a preference list.
 * Can be either a [PreferenceKey] or a [app.aaps.core.ui.compose.preference.PreferenceSubScreen].
 *
 * Note: Not sealed to allow cross-module implementation.
 */
interface PreferenceItem

/**
 * Defines shared preference encapsulation
 */
interface PreferenceKey : NonPreferenceKey, PreferenceItem {

    /**
     * Associated [android.content.SharedPreferences] key
     */
    override val key: String

    /**
     * String resource ID for preference title.
     * Use ResourceHelper.gs(titleResId) for localized string.
     */
    val titleResId: Int

    /**
     * String resource ID for preference summary/description.
     * Use ResourceHelper.gs(summaryResId) for localized string.
     * null means no summary.
     */
    val summaryResId: Int?
        get() = null

    /**
     * UI type for rendering this preference.
     * Determines which Adaptive* composable to use.
     * Each key type provides a sensible default.
     */
    val preferenceType: PreferenceType
        get() = PreferenceType.TEXT_FIELD

    /**
     * Affected by simple mode?
     *
     * If yes: in simpleMode default value is always used and shared preference value is ignored.
     * If not: value from shared preferences is used.
     */
    val defaultedBySM: Boolean

    /**
     * Show only when APS mode is active (ie not PumpControl and NsClient)
     */
    val showInApsMode: Boolean

    /**
     * Show only when NsClient mode is active
     */
    val showInNsClientMode: Boolean

    /**
     * Show only when PumpControl mode is active
     */
    val showInPumpControlMode: Boolean

    /**
     * show only if master dependency is enabled (ie android:dependency behavior)
     */
    val dependency: BooleanPreferenceKey?

    /**
     * show only if master dependency is disabled (ie negative android:dependency behavior)
     */
    val negativeDependency: BooleanPreferenceKey?

    /**
     * Hide parent screen
     * PreferenceScreen is final so we cannot extend and modify thisbehavior
     */
    val hideParentScreenIfHidden: Boolean

    /**
     * Runtime visibility condition for this preference.
     * Evaluated against [PreferenceVisibilityContext] to determine if preference should be shown.
     * Default is [PreferenceVisibility.ALWAYS] (always visible).
     *
     * Example usage in key definition:
     * ```
     * IageWarning(..., visibility = PreferenceVisibility.NON_PATCH_PUMP)
     * ```
     */
    val visibility: PreferenceVisibility
        get() = PreferenceVisibility.ALWAYS

    /**
     * Runtime enabled condition for this preference.
     * Evaluated against [PreferenceVisibilityContext] to determine if preference should be enabled.
     * Default is [PreferenceEnabledCondition.ALWAYS] (always enabled).
     *
     * Example usage in key definition:
     * ```
     * SmsRemoteBolusDistance(..., enabledCondition = PreferenceEnabledCondition { ctx ->
     *     ctx.preferences.get(StringKey.SmsAllowedNumbers).split(";").size >= 2
     * })
     * ```
     */
    val enabledCondition: PreferenceEnabledCondition
        get() = PreferenceEnabledCondition.ALWAYS

    /**
     * Unit type for this preference value.
     * Determines how values are formatted with units in UI.
     * Use [UnitType.valueResId] and [UnitType.rangeResId] extension functions
     * to get format string resource IDs.
     */
    val unitType: UnitType
        get() = UnitType.NONE
}