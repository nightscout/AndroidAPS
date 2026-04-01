package app.aaps.core.keys.interfaces

/**
 * Context provided to [PreferenceVisibility] lambdas for evaluating
 * whether a preference should be visible.
 *
 * This interface abstracts away the need for preferences to know about
 * specific plugin implementations while still allowing runtime visibility decisions.
 */
interface PreferenceVisibilityContext {

    /**
     * Whether the active pump is a patch pump (like Omnipod).
     * Patch pumps don't have replaceable insulin cartridges, so insulin age preferences are hidden.
     */
    val isPatchPump: Boolean

    /**
     * Whether the active pump has a replaceable battery.
     */
    val isBatteryReplaceable: Boolean

    /**
     * Whether the active pump logs battery changes (even if battery isn't user-replaceable).
     */
    val isBatteryChangeLoggingEnabled: Boolean

    /**
     * Whether the active BG source supports advanced filtering (for SMB-related preferences).
     */
    val advancedFilteringSupported: Boolean

    /**
     * Access to preferences for checking other preference values.
     * Useful for dependent visibility (e.g., show PIN field only if protection type is PIN).
     */
    val preferences: Preferences

    /**
     * Whether the pump is currently paired/connected.
     * Used by pump plugins like ComboV2 to enable/disable pairing preferences.
     */
    val isPumpPaired: Boolean
        get() = false

    /**
     * Whether the pump is initialized and ready for operation.
     * Used by pump plugins like Medtrum to disable serial input after initialization.
     */
    val isPumpInitialized: Boolean
        get() = false
}

/**
 * Helper function to check if an IntKey equals a specific value.
 * Useful for conditional visibility based on enum-like IntKey values.
 *
 * Example:
 * ```
 * runtimeVisibility = { ctx ->
 *     ctx.intEquals(IntKey.ProtectionTypeSettings, ProtectionType.CUSTOM_PASSWORD.ordinal)
 * }
 * ```
 */
fun PreferenceVisibilityContext.intEquals(key: IntPreferenceKey, value: Int): Boolean =
    preferences.get(key) == value

/**
 * Helper function to check if an IntKey is one of several values.
 * Useful for conditional visibility based on multiple enum values.
 *
 * Example:
 * ```
 * runtimeVisibility = { ctx ->
 *     ctx.intIn(IntKey.ProtectionType, setOf(2, 3, 4)) // Any password type
 * }
 * ```
 */
fun PreferenceVisibilityContext.intIn(key: IntPreferenceKey, values: Set<Int>): Boolean =
    preferences.get(key) in values
