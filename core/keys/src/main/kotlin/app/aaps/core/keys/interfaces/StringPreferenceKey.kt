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
     * Map of stored value -> label.
     * Empty map means no entries (not a list preference).
     */
    val entries: Map<String, TextRef>
        get() = emptyMap()

    /**
     * Validator for the string value.
     * Used to validate input before accepting it.
     * Default is no validation.
     */
    val validator: StringValidator
        get() = StringValidator.NONE
}

/**
 * Wrapper that attaches entries to a StringPreferenceKey.
 * Uses delegation to preserve all other properties from the original key.
 */
class StringKeyWithEntries(
    private val delegate: StringPreferenceKey,
    override val entries: Map<String, TextRef>
) : StringPreferenceKey by delegate

/**
 * Creates a new StringPreferenceKey with entries attached.
 * Use this when the entries are only known at run time - for example a list that depends on the
 * connected device, or on values computed from another setting.
 *
 * @param entries Map of stored value -> label. Use [TextRef.Res] with arguments for anything the
 *   user reads, so it stays translatable; [TextRef.Literal] only for text that is genuinely not a
 *   resource, such as a device name.
 * @return A new StringPreferenceKey with the entries attached
 */
fun StringPreferenceKey.withEntries(entries: Map<String, TextRef>): StringPreferenceKey =
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