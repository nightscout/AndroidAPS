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
    val recordOnlyChecked: Boolean = false,
    val notes: String = "",
    val eventTime: Long = System.currentTimeMillis(),
    val eventTimeOriginal: Long = System.currentTimeMillis(),
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
    val forcedRecordOnly: Boolean = false
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
    get() = insulin > 0.0 || eatingSoonTtChecked
