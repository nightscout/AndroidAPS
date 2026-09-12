package app.aaps.core.data.model

/**
 * Temporary Target preset configuration.
 * Stores user-defined and default TT presets for quick activation.
 * Serialization is handled externally using JSON library.
 */
data class TTPreset(
    /** Unique identifier for this preset */
    val id: String,
    /** Display name for custom presets (null for default presets, which use [displayName]) */
    val name: String? = null,
    /**
     * The already translated name of a default preset, filled in when the list is read.
     *
     * Not persisted, and deliberately not a resource id: this class is multiplatform, and an Android
     * `R.string` id is neither portable nor stable (the ids change between builds, which is why this
     * was never written to storage even when it was one). The text is resolved from [reason] by
     * `withDisplayName`, so a language change re-fills it when the list is read again.
     */
    val displayName: String? = null,
    /** Reason/category for this temporary target */
    val reason: TT.Reason,
    /** Target value ALWAYS in mg/dL (converted at display time) */
    val targetValue: Double,
    /** Duration in milliseconds (matches database format) */
    val duration: Long,
    /** Whether this preset can be deleted (false for default presets) */
    val isDeletable: Boolean = true
)
