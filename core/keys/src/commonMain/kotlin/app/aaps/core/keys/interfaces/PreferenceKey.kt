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
     * Preference title.
     * Use `ResourceHelper.gs(title)` outside Compose, or `stringResource(title)` inside it.
     */
    val title: TextRef

    /**
     * Preference summary/description.
     * null means no summary - there is no "empty" sentinel any more.
     */
    val summary: TextRef?
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
        get() = false

    /**
     * Which platforms this preference is shown on. Everywhere unless it says otherwise.
     *
     * The same flag serves the settings screen, the search index and the hidden-value check, so a
     * key that names its platforms disappears from all three at once and cannot be left half gated.
     *
     * Use it when a platform has **no way to honour the setting** - `AlertUrgentAsAndroidNotification`
     * describes Android's notification policy and there is nothing for it to mean on iOS, where the
     * user controls that in Settings. A row that is drawn and wired to nothing is worse than an
     * absent one: it reads as a promise.
     *
     * Do **not** use it for a key that is synced. A `Bidirectional` key shown on a client is
     * configuring the *master*, so the platform doing the displaying is not the platform that has to
     * honour it, and restricting it would hide a control that works. `PreferencePlatformRulesTest`
     * enforces that.
     *
     * Do **not** use it for "not built yet" either. This says a platform *cannot* do a thing, not
     * that nobody has got round to it - otherwise the flag quietly becomes a list of unfinished work
     * that no one is reminded of.
     */
    val platforms: Set<AppPlatform>
        get() = AppPlatform.ALL

    /**
     * Show when APS mode is active (ie not PumpControl and NsClient). Set false to hide it there.
     *
     * Defaulted here, like [platforms] beside it. Without a default every implementor had to declare
     * all three of these, so 38 of the 40 key enums carried the same three lines saying "yes, yes,
     * yes" - `= true` repeated 114 times, and three more to copy each time a driver added a key. Only
     * [app.aaps.core.keys.BooleanKey] and Instara's keys ever set one.
     */
    val showInApsMode: Boolean
        get() = true

    /** Show when NsClient mode is active. Set false to hide it there. See [showInApsMode]. */
    val showInNsClientMode: Boolean
        get() = true

    /** Show when PumpControl mode is active. Set false to hide it there. See [showInApsMode]. */
    val showInPumpControlMode: Boolean
        get() = true

    /**
     * show only if master dependency is enabled (ie android:dependency behavior)
     */
    val dependency: BooleanPreferenceKey?
        get() = null

    /**
     * show only if master dependency is disabled (ie negative android:dependency behavior)
     *
     * Nothing sets this today, and nothing ever has - not on this branch and not before the
     * multiplatform split, so it is not a caller some port dropped. `PreferenceState` still honours
     * it, so it works if a key wants it; [visibility] is what has been reached for instead, and it
     * can express conditions this cannot. See `ApsUseAutosens`, which says in its own comment why a
     * plain negative dependency was not enough for it.
     */
    val negativeDependency: BooleanPreferenceKey?
        get() = null

    /**
     * Hide parent screen
     * PreferenceScreen is final so we cannot extend and modify thisbehavior
     */
    val hideParentScreenIfHidden: Boolean
        get() = false

    /**
     * Runtime visibility condition for this preference.
     * Evaluated against [VisibilityContext] to determine if preference should be shown.
     * Default is [ElementVisibility.ALWAYS] (always visible).
     *
     * Example usage in key definition:
     * ```
     * IageWarning(..., visibility = PreferenceVisibility.NON_PATCH_PUMP)
     * ```
     */
    val visibility: ElementVisibility
        get() = ElementVisibility.ALWAYS

    /**
     * Runtime enabled condition for this preference.
     * Evaluated against [VisibilityContext] to determine if preference should be enabled.
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