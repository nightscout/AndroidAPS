package app.aaps.core.keys.interfaces

/**
 * Functional interface for determining preference enabled state at runtime.
 *
 * This allows preferences to declare their enabled conditions declaratively
 * in the key definition rather than imperatively in UI code.
 *
 * Usage in key definition:
 * ```
 * SmsRemoteBolusDistance(..., enabledCondition = PreferenceEnabledCondition { ctx ->
 *     // Enable only when multiple phone numbers are configured
 *     ctx.preferences.get(StringKey.SmsAllowedNumbers).split(";").size >= 2
 * })
 * ```
 */
fun interface PreferenceEnabledCondition {

    /**
     * Evaluates whether the preference should be enabled given the current context.
     *
     * @param context The visibility context providing access to pump, BG source, and preference state
     * @return true if the preference should be enabled, false otherwise
     */
    fun isEnabled(context: PreferenceVisibilityContext): Boolean

    companion object {

        /**
         * Always enabled (default for most preferences)
         */
        val ALWAYS = PreferenceEnabledCondition { true }
    }
}
