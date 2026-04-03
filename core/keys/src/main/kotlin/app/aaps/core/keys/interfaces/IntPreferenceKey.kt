package app.aaps.core.keys.interfaces

interface IntPreferenceKey : PreferenceKey, IntNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: Int

    /**
     *  Minimal allowed value
     */
    val min: Int

    /**
     *  Maximal allowed value
     */
    val max: Int

    /**
     *  Value with calculation of default value
     */
    val calculatedDefaultValue: Boolean

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean

    /**
     * Entries for LIST type preferences.
     * Map of stored value -> label resource ID.
     * Empty map means no entries (not a list preference).
     */
    val entries: Map<Int, Int>
        get() = emptyMap()

    /**
     * Runtime-resolved entries for LIST type preferences.
     * Map of stored value -> resolved label string.
     * When set, this takes precedence over [entries] resource IDs.
     */
    val resolvedEntries: Map<Int, String>?
        get() = null
}

/**
 * Wrapper that attaches runtime-resolved entries to an IntPreferenceKey.
 * Uses delegation to preserve all other properties from the original key.
 */
class IntKeyWithEntries(
    private val delegate: IntPreferenceKey,
    override val resolvedEntries: Map<Int, String>
) : IntPreferenceKey by delegate

/**
 * Creates a new IntPreferenceKey with runtime-resolved entries attached.
 * Use this when entries need to be resolved at runtime (e.g., programmatic values).
 *
 * @param entries Map of stored value -> resolved label string
 * @return A new IntPreferenceKey with the entries attached
 */
fun IntPreferenceKey.withEntries(entries: Map<Int, String>): IntPreferenceKey =
    IntKeyWithEntries(this, entries)