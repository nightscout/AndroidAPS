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
        get() = false

    /**
     * Visible in engineering mode only, otherwise `defaultValue`
     */
    val engineeringModeOnly: Boolean
        get() = false

    /**
     * Entries for LIST type preferences.
     * Map of stored value -> label.
     * Empty map means no entries (not a list preference).
     */
    val entries: Map<Int, TextRef>
        get() = emptyMap()

}

/**
 * Wrapper that attaches entries to an IntPreferenceKey.
 * Uses delegation to preserve all other properties from the original key.
 */
class IntKeyWithEntries(
    private val delegate: IntPreferenceKey,
    override val entries: Map<Int, TextRef>
) : IntPreferenceKey by delegate

/**
 * Creates a new IntPreferenceKey with entries attached.
 * Use this when the entries are only known at run time - a generated range, or a list that depends
 * on the connected device.
 *
 * @param entries Map of stored value -> label. Use [TextRef.AndroidRes] with arguments for anything the
 *   user reads, so it stays translatable; [TextRef.Literal] only for text that is genuinely not a
 *   resource, such as a device name.
 * @return A new IntPreferenceKey with the entries attached
 */
fun IntPreferenceKey.withEntries(entries: Map<Int, TextRef>): IntPreferenceKey =
    IntKeyWithEntries(this, entries)