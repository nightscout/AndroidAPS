package app.aaps.core.keys.interfaces

/**
 * Defines shared preference encapsulation that works inside a module without preferences UI
 */
interface NonPreferenceKey {

    /**
     * Associated [android.content.SharedPreferences] key
     */
    val key: String

    /**
     * If true, this preference is exported
     */
    val exportable: Boolean
}