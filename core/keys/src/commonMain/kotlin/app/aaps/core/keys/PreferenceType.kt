package app.aaps.core.keys

/**
 * Defines the UI type for rendering a preference.
 * Used to determine which Adaptive* composable to use.
 */
enum class PreferenceType {

    /** Default for BooleanPreferenceKey - renders as a switch toggle */
    SWITCH,

    /** Default for IntPreferenceKey, DoublePreferenceKey - renders as text input field */
    TEXT_FIELD,

    /** Renders as a dropdown/dialog list (requires entries) */
    LIST,

    /** Default for IntentPreferenceKey - clickable preference that invokes onClick */
    CLICK,

    /** Opens a URL in browser (for IntentPreferenceKey) */
    URL,

    /** Launches an Activity (for IntentPreferenceKey) */
    ACTIVITY
}
