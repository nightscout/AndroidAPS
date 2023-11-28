package app.aaps.core.keys

/**
 * Defines shared preference encapsulation
 */
interface PreferenceKey {

    /**
     * Associated [android.content.SharedPreferences] key
     */
    val key: Int

    /**
     * Affected by simple mode?
     *
     * If yes: in simpleMode default value is always used and shared preference value is ignored.
     * If not: value from shared preferences is used.
     */
    val defaultedBySM: Boolean
}