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

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
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
    val guard: () -> String?
) : BooleanPreferenceKey by delegate

/**
 * Creates a new BooleanPreferenceKey with a change guard attached.
 * The guard is called when the user tries to turn the preference off.
 * Return null to allow, or an error message string to block the change.
 */
fun BooleanPreferenceKey.withChangeGuard(
    guard: () -> String?
): BooleanKeyWithChangeGuard =
    BooleanKeyWithChangeGuard(this, guard)
