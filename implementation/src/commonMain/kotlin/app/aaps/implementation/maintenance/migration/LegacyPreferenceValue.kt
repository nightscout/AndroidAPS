package app.aaps.implementation.maintenance.migration

/**
 * Reads a value out of the raw preference store as the type a migration expects, or reports that it
 * cannot.
 *
 * The raw store holds whatever any AAPS version, any import file or any third party ever wrote, so a
 * key whose name says "total" does not have to hold a `Long`. The migrations used to cast
 * (`value as Long`), and a cast that fails during start up is caught in `MainApp.onCreate` as "Fatal
 * initialization error" - the app does not start at all until the user clears its data.
 *
 * Every function here returns null instead of throwing, and null means SKIP: leave the old key alone,
 * log it, let the next run try again. None of them guesses a replacement value. That is the whole
 * point, and it is the thing to preserve if this is ever edited:
 *
 * - `toBooleanStrictOrNull`, never `toBoolean`. `toBoolean` maps anything that is not "true" to false,
 *   so a broken value would migrate as a real `false` and turn a plugin off.
 * - [asString] rejects non-text instead of calling `toString()`.
 *
 * ## Two stores, one helper
 *
 * The migrations now run against two different stores. On a device the values are natively typed -
 * `Boolean`, `Int`, `Long`, `Float`, `String`. In an imported file EVERY value is text, because the
 * exporter writes `value.toString()`. [asLong], [asBoolean] and [asDouble] accept both, which is what
 * makes one migration correct on both paths.
 *
 * [asString] is the exception, and [asJsonArrayText] exists because of it.
 */
object LegacyPreferenceValue {

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

    fun asDouble(value: Any?): Double? = when (value) {
        is Double -> value
        is Float  -> value.toDouble()
        is Int    -> value.toDouble()
        is Long   -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else      -> null
    }

    /** Free text, for a value that really is free text - a profile's name, for instance. */
    fun asString(value: Any?): String? = value as? String

    /**
     * Text shaped like a JSON array, for a profile's ISF, IC, basal and target schedules.
     *
     * [asString] is not enough for these, and the reason is the difference between the two stores
     * above. On a device it refuses a value that is not text, so a number could never be taken for a
     * schedule. Every value in a FILE is text, so that guard stops nothing there: `"5.5"` would sail
     * through and be written as a profile's insulin sensitivity schedule.
     *
     * A bracket test rather than a parse. The question is only whether the value is the right KIND of
     * thing; `ProfileRepositoryImpl` does the real parsing, and a second parser here would be a second
     * way to disagree with it.
     */
    fun asJsonArrayText(value: Any?): String? = (value as? String)?.takeIf { it.trimStart().startsWith("[") }

    /** What a value turned out to be, for a log line that has to explain a skip without printing it. */
    fun describeType(value: Any?): String = value?.let { it::class.simpleName } ?: "nothing"
}
