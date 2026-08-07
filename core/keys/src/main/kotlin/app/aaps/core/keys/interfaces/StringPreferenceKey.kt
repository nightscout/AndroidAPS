package app.aaps.core.keys.interfaces

interface StringPreferenceKey : PreferenceKey, StringNonPreferenceKey {

    /**
     * Default value if not changed from preferences
     */
    override val defaultValue: String
    val isPassword: Boolean
    val isPin: Boolean

    /**
     * Whether the value should be hashed before storing.
     * When true, [hashPassword] is applied before persisting.
     * When false, the value is stored as plaintext (even if [isPassword] masks the UI).
     */
    val isHashed: Boolean
        get() = false

    /**
     * Entries for LIST type preferences.
     * Map of stored value -> label resource ID.
     * Empty map means no entries (not a list preference).
     */
    val entries: Map<String, Int>
        get() = emptyMap()

    /**
     * Runtime-resolved entries for LIST type preferences.
     * Map of stored value -> resolved label string.
     * When set, this takes precedence over [entries] resource IDs.
     */
    val resolvedEntries: Map<String, String>?
        get() = null

    /**
     * Validator for the string value.
     * Used to validate input before accepting it.
     * Default is no validation.
     */
    val validator: StringValidator
        get() = StringValidator.NONE
}

/**
 * Wrapper that attaches runtime-resolved entries to a StringPreferenceKey.
 * Uses delegation to preserve all other properties from the original key.
 */
class StringKeyWithEntries(
    private val delegate: StringPreferenceKey,
    override val resolvedEntries: Map<String, String>
) : StringPreferenceKey by delegate

/**
 * Creates a new StringPreferenceKey with runtime-resolved entries attached.
 * Use this when entries need to be resolved at runtime (e.g., from plugins).
 *
 * @param entries Map of stored value -> resolved label string
 * @return A new StringPreferenceKey with the entries attached
 */
fun StringPreferenceKey.withEntries(entries: Map<String, String>): StringPreferenceKey =
    StringKeyWithEntries(this, entries)

/**
 * Wrapper that attaches a runtime entries provider to a StringPreferenceKey.
 * The provider is called at compose time, and the labels it returns are resolved there.
 * If the provider returns an empty map, shows a disabled preference with the empty message.
 */
class StringKeyWithEntriesProvider(
    private val delegate: StringPreferenceKey,
    val entriesProvider: () -> Map<String, TextRef>,
    val emptyEntriesMessage: TextRef? = null
) : StringPreferenceKey by delegate

/**
 * Creates a new StringPreferenceKey with a runtime entries provider.
 * Use this when the set of entries is only known at run time - for example when it depends on the
 * connected pump model, or on devices found by a scan.
 *
 * @param provider Function returning Map of stored value -> label
 * @param emptyEntriesMessage Optional message to show when entries are empty
 * @return A new StringKeyWithEntriesProvider
 */
fun StringPreferenceKey.withEntriesProvider(
    provider: () -> Map<String, TextRef>,
    emptyEntriesMessage: TextRef? = null
): StringKeyWithEntriesProvider =
    StringKeyWithEntriesProvider(this, provider, emptyEntriesMessage)