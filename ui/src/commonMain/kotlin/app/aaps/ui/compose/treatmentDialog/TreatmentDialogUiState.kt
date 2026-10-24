package app.aaps.ui.compose.treatmentDialog

import androidx.compose.runtime.Immutable

@Immutable
data class TreatmentDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val carbs: Int = 0,
    // Config (set once during init)
    val maxInsulin: Double = 0.0,
    val maxCarbs: Int = 0,
    val bolusStep: Double = 0.1,
    val isAapsClient: Boolean = false,
    /** True when the entry must be recorded only (no pump delivery): pump unavailable or the mode forbids bolus. (A client delivers via the master, not record-only.) */
    val forcedRecordOnly: Boolean = false,
    /** An SMB is being delivered on this phone (always false on a client: its master decides). */
    val smbBeingDelivered: Boolean = false
)

/** OK waits: the pump would give this bolus, and an SMB is being delivered right now (#5381). */
val TreatmentDialogUiState.waitsForSmb: Boolean
    get() = smbBeingDelivered && insulin > 0.0 && !forcedRecordOnly
