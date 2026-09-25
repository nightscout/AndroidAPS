package app.aaps

/**
 * Reads a value out of the raw preference store as the type a migration expects, or reports that it
 * cannot.
 *
 * The raw store holds whatever any AAPS version, any import file or any third party ever wrote, so
 * a key whose name says "total" does not have to hold a `Long`. The start-up migrations in `MainApp`
 * used to cast (`value as Long`), and a cast that fails there is caught as "Fatal initialization
 * error" - the app does not start at all until the user clears its data.
 *
 * Every function here returns null instead of throwing, and null means SKIP: leave the old key
 * alone, log it, let the next start try again. None of them guesses a replacement value. That is the
 * whole point, and it is the thing to preserve if this is ever edited:
 *
 * - `toBooleanStrictOrNull`, never `toBoolean`. `toBoolean` maps anything that is not "true" to
 *   false, so a broken value would migrate as a real `false` and turn a plugin off.
 * - [asString] rejects non-text instead of calling `toString()`. These values are the profile's ISF,
 *   IC, basal and target arrays; a number rendered as text would be taken downstream as a profile.
 */
internal object LegacyPreferenceValue {

    /** `Int` is included because that is what the store returns for a value written as an int. */
    fun asLong(value: Any?): Long? = when (value) {
        is Long   -> value
        is Int    -> value.toLong()
        is String -> value.toLongOrNull()
        else      -> null
    }

    fun asBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String  -> value.toBooleanStrictOrNull()
        else       -> null
    }

    fun asString(value: Any?): String? = value as? String
}
