package app.aaps.core.keys.interfaces

/**
 * Functional interface for determining preference visibility at runtime.
 *
 * This allows preferences to declare their visibility conditions declaratively
 * in the key definition rather than imperatively in UI code.
 *
 * Usage in key definition:
 * ```
 * IageWarning(..., visibility = PreferenceVisibility.NON_PATCH_PUMP)
 * ```
 *
 * Custom visibility:
 * ```
 * SomeKey(..., visibility = PreferenceVisibility { it.preferences.get(BooleanKey.SomeFlag) })
 * ```
 */
fun interface PreferenceVisibility {

    /**
     * Evaluates whether the preference should be visible given the current context.
     *
     * @param context The visibility context providing access to pump, BG source, and preference state
     * @return true if the preference should be visible, false otherwise
     */
    fun isVisible(context: PreferenceVisibilityContext): Boolean

    companion object {

        /**
         * Always visible (default for most preferences)
         */
        val ALWAYS = PreferenceVisibility { true }

        /**
         * Visible only for non-patch pumps (e.g., insulin age preferences)
         */
        val NON_PATCH_PUMP = PreferenceVisibility { !it.isPatchPump }

        /**
         * Visible only for patch pumps
         */
        val PATCH_PUMP_ONLY = PreferenceVisibility { it.isPatchPump }

        /**
         * Visible only when pump battery is replaceable or battery change logging is enabled
         */
        val BATTERY_REPLACEABLE = PreferenceVisibility {
            it.isBatteryReplaceable || it.isBatteryChangeLoggingEnabled
        }

        /**
         * Visible only when BG source supports advanced filtering (for certain SMB options)
         */
        val ADVANCED_FILTERING = PreferenceVisibility { it.advancedFilteringSupported }

        /**
         * Creates a visibility condition that checks if an IntKey equals a specific value.
         */
        fun intEquals(key: IntPreferenceKey, value: Int) = PreferenceVisibility { ctx ->
            ctx.intEquals(key, value)
        }

        /**
         * Creates a visibility condition that checks if an IntKey equals a specific value.
         * Uses a lazy key provider to avoid circular enum class initialization.
         */
        fun intEquals(keyProvider: () -> IntPreferenceKey, value: Int) = PreferenceVisibility { ctx ->
            ctx.intEquals(keyProvider(), value)
        }

        /**
         * Creates a visibility condition that checks if a StringKey is not empty.
         */
        fun stringNotEmpty(key: StringPreferenceKey) = PreferenceVisibility { ctx ->
            ctx.preferences.get(key).isNotEmpty()
        }

        /**
         * Creates a visibility condition that checks if a StringKey is not empty.
         * Uses a lazy key provider to avoid circular enum class initialization.
         */
        fun stringNotEmpty(keyProvider: () -> StringPreferenceKey) = PreferenceVisibility { ctx ->
            ctx.preferences.get(keyProvider()).isNotEmpty()
        }
    }
}
