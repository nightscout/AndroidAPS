package app.aaps.ui.compose.insulinDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg

@Immutable
data class InsulinDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val timeOffsetMinutes: Int = 0,
    val eatingSoonTtChecked: Boolean = false,
    /** False on a client whose role may give insulin but not set a temp target: the switch is not offered. */
    val canSetTempTarget: Boolean = true,
    val recordOnlyChecked: Boolean = false,
    val notes: String = "",
    val eventTime: Long = 0L,
    val eventTimeOriginal: Long = 0L,
    val selectedIcfg: ICfg? = null,
    val insulins: List<ICfg> = emptyList(),

    // Config (set once during init)
    val maxInsulin: Double = 0.0,
    val bolusStep: Double = 0.1,
    val insulinButtonIncrement1: Double = 0.5,
    val insulinButtonIncrement2: Double = 1.0,
    val insulinButtonIncrement3: Double = 2.0,
    val eatingSoonTtTarget: Double = 0.0,
    val eatingSoonTtDuration: Int = 0,
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val showNotesFromPreferences: Boolean = false,
    val simpleMode: Boolean = true,
    val isAapsClient: Boolean = false,
    val forcedRecordOnly: Boolean = false,
    /** An SMB is being delivered on this phone (always false on a client: its master decides). */
    val smbBeingDelivered: Boolean = false
)

val InsulinDialogUiState.eventTimeChanged: Boolean
    get() = eventTime != eventTimeOriginal

val InsulinDialogUiState.timeLayoutVisible: Boolean
    get() = recordOnlyChecked

val InsulinDialogUiState.recordOnlyEnabled: Boolean
    // A client CAN record-only: the entry goes through the batch → master (SSOT), so the toggle is enabled
    // there too. Only a forced record-only (master can't deliver) locks it (checked + non-editable).
    get() = !forcedRecordOnly

val InsulinDialogUiState.confirmEnabled: Boolean
    get() = (insulin > 0.0 || eatingSoonTtChecked) && !waitsForSmb

/** OK waits: the pump would give this bolus, and an SMB is being delivered right now (#5381). */
val InsulinDialogUiState.waitsForSmb: Boolean
    get() = smbBeingDelivered && insulin > 0.0 && !recordOnlyChecked

/**
 * The step the amount field is formatted with. A record-only dose is kept as typed (a pen can give 1.33 U), so it
 * is shown with 2 decimals, the format of a 0.01 step, and not with the step of the pump.
 */
val InsulinDialogUiState.displayStep: Double
    get() = if (recordOnlyChecked) RECORD_ONLY_DISPLAY_STEP else bolusStep

private const val RECORD_ONLY_DISPLAY_STEP = 0.01
