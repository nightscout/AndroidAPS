package app.aaps.core.keys.interfaces

/**
 * Defines shared preference encapsulation
 */
interface PreferenceKey : NonPreferenceKey {

    /**
     * Associated [android.content.SharedPreferences] key
     */
    override val key: String

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
}