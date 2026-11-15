package app.aaps.core.keys.interfaces

interface BooleanPreferenceKey : PreferenceKey, BooleanNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Boolean

    /**
     * Default value is calculated instead of `defaultValue`
     */
    val calculatedDefaultValue: Boolean
        get() = false

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
        get() = false

    /**
     * Affected by hardened mode ([Preferences.hardenedMode])?
     *
     * If yes: while hardened mode is on, the default value is always used, the stored value is ignored
     * and the row is hidden. The default of such a key must therefore be its hardened value: do not
     * accept from Nightscout, do upload, use the WebSocket.
     *
     * Like [defaultedBySM], but for hardened mode. Simple mode turns hardened mode on, but it is not
     * the same thing: a user who is not in simple mode can turn hardened mode on as well.
     */
    val defaultedByHardenedMode: Boolean
        get() = false
}

/**
 * Wrapper that attaches a change guard to a BooleanPreferenceKey.
 * The guard is called before the value changes. If it returns a non-null string,
 * the change is blocked and the string is shown as a message to the user.
 *
 * @param delegate The original key
 * @param guard Called with the proposed new value. Return null to allow, or an error message to block.
 */
class BooleanKeyWithChangeGuard(
    private val delegate: BooleanPreferenceKey,
    val guard: (newValue: Boolean) -> String?
) : BooleanPreferenceKey by delegate

/**
 * Creates a new BooleanPreferenceKey with a change guard attached.
 * The guard is called when the user tries to change the preference.
 * Return null to allow, or an error message string to block the change.
 *
 * @param guard Called with the proposed new value (true = turning on, false = turning off).
 */
fun BooleanPreferenceKey.withChangeGuard(
    guard: (newValue: Boolean) -> String?
): BooleanKeyWithChangeGuard =
    BooleanKeyWithChangeGuard(this, guard)
