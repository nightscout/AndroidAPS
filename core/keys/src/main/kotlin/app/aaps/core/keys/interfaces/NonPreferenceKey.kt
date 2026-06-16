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

    /**
     * Device-to-device sync classification (channel + authority). `null` (the default) means the key
     * is not synced. The single source of truth for sync membership — see [SyncSpec].
     */
    val sync: SyncSpec? get() = null
}