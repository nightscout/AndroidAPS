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
     * If true, this preference is exported. Set false to keep it out of an export.
     *
     * Defaulted, because 92 key enums declared it and every one of them said `true`. The handful
     * that mean `false` are the non-preference keys, which say so themselves.
     */
    val exportable: Boolean
        get() = true

    /**
     * Device-to-device sync classification (channel + authority). `null` (the default) means the key
     * is not synced. The single source of truth for sync membership — see [SyncSpec].
     */
    val sync: SyncSpec? get() = null
}