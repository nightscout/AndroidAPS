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
 * Wrapper that attaches a context-dependent entries provider to a StringPreferenceKey.
 * The provider is called at compose time with the current Context.
 * If the provider returns an empty map, shows a disabled preference with the empty message.
 */
class StringKeyWithEntriesProvider(
    private val delegate: StringPreferenceKey,
    val entriesProvider: (android.content.Context) -> Map<String, String>,
    val emptyEntriesMessageResId: Int? = null
) : StringPreferenceKey by delegate

/**
 * Creates a new StringPreferenceKey with a context-dependent entries provider.
 * Use this when entries need to be resolved at compose time with Context access
 * (e.g., Bluetooth devices requiring permission checks).
 *
 * @param provider Function that takes Context and returns Map of stored value -> label
 * @param emptyEntriesMessageResId Optional resource ID for message to show when entries are empty
 * @return A new StringKeyWithEntriesProvider
 */
fun StringPreferenceKey.withEntriesProvider(
    provider: (android.content.Context) -> Map<String, String>,
    emptyEntriesMessageResId: Int? = null
): StringKeyWithEntriesProvider =
    StringKeyWithEntriesProvider(this, provider, emptyEntriesMessageResId)