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
fun interface ElementVisibility {

    /**
     * Evaluates whether the element should be visible given the current context.
     *
     * @param context The visibility context providing access to pump, BG source, and preference state
     * @return true if the element should be visible, false otherwise
     */
    fun isVisible(context: VisibilityContext): Boolean

    companion object {

        /**
         * Always visible (default for most preferences)
         */
        val ALWAYS = ElementVisibility { true }

        /**
         * Visible everywhere on a master; on a client (AAPSCLIENT) visible only once paired with a
         * master ([VisibilityContext.masterOrPairedClient]). Gates mutating/command UI whose actions
         * ride the signed Client-Control channel — an unpaired client's commands are silently dropped,
         * so those entry points are hidden until pairing exists. Read-only/monitoring UI and the
         * pairing escape hatch must NOT use this. (`!isClient` short-circuits to always-visible on a master.)
         */
        val MASTER_OR_PAIRED_CLIENT = ElementVisibility { !it.isClient || it.masterOrPairedClient }

        /**
         * Visible only for non-patch pumps (e.g., insulin age preferences)
         */
        val NON_PATCH_PUMP = ElementVisibility { !it.isPatchPump }

        /**
         * Visible only for patch pumps
         */
        val PATCH_PUMP_ONLY = ElementVisibility { it.isPatchPump }

        /**
         * Visible only when pump battery is replaceable or battery change logging is enabled
         */
        val BATTERY_REPLACEABLE = ElementVisibility {
            it.isBatteryReplaceable || it.isBatteryChangeLoggingEnabled
        }

        /**
         * Visible only when BG source supports advanced filtering (for certain SMB options)
         */
        val ADVANCED_FILTERING = ElementVisibility { it.advancedFilteringSupported }

        /**
         * Creates a visibility condition that checks if an IntKey equals a specific value.
         */
        fun intEquals(key: IntPreferenceKey, value: Int) = ElementVisibility { ctx ->
            ctx.intEquals(key, value)
        }

        /**
         * Creates a visibility condition that checks if an IntKey equals a specific value.
         * Uses a lazy key provider to avoid circular enum class initialization.
         */
        fun intEquals(keyProvider: () -> IntPreferenceKey, value: Int) = ElementVisibility { ctx ->
            ctx.intEquals(keyProvider(), value)
        }

        /**
         * Creates a visibility condition that checks if a StringKey is not empty.
         */
        fun stringNotEmpty(key: StringPreferenceKey) = ElementVisibility { ctx ->
            ctx.preferences.get(key).isNotEmpty()
        }

        /**
         * Creates a visibility condition that checks if a StringKey is not empty.
         * Uses a lazy key provider to avoid circular enum class initialization.
         */
        fun stringNotEmpty(keyProvider: () -> StringPreferenceKey) = ElementVisibility { ctx ->
            ctx.preferences.get(keyProvider()).isNotEmpty()
        }
    }
}
