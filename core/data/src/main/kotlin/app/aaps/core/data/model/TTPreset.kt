package app.aaps.core.data.model

/**
 * Temporary Target preset configuration.
 * Stores user-defined and default TT presets for quick activation.
 * Serialization is handled externally using JSON library.
 */
data class TTPreset(
    /** Unique identifier for this preset */
    val id: String,
    /** Display name for custom presets (null for default presets that use nameRes) */
    val name: String? = null,
    /** String resource ID for default presets (null for custom presets that use name) */
    val nameRes: Int? = null,
    /** Reason/category for this temporary target */
    val reason: TT.Reason,
    /** Target value ALWAYS in mg/dL (converted at display time) */
    val targetValue: Double,
    /** Duration in milliseconds (matches database format) */
    val duration: Long,
    /** Whether this preset can be deleted (false for default presets) */
    val isDeletable: Boolean = true
)
