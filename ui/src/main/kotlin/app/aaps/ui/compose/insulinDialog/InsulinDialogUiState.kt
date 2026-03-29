package app.aaps.ui.compose.insulinDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg

enum class RecordSource { PUMP, PEN }

@Immutable
data class InsulinDialogUiState(
    // User input
    val insulin: Double = 0.0,
    val timeOffsetMinutes: Int = 0,
    val eatingSoonTtChecked: Boolean = false,
    val recordOnlyChecked: Boolean = false,
    val recordSource: RecordSource = RecordSource.PUMP,
    val notes: String = "",
    val eventTime: Long = System.currentTimeMillis(),
    val eventTimeOriginal: Long = System.currentTimeMillis(),
    val iCfg: ICfg? = null,
    val insulins: ArrayList<ICfg> = ArrayList(),
    val penIcfg: ICfg? = null,

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
    get() = !forcedRecordOnly && !isAapsClient

val InsulinDialogUiState.u100Insulins: List<ICfg>
    get() = insulins.filter { it.concentration == 1.0 }

/** True when insulin selection requirement is satisfied (always true for pump, requires penIcfg for pen) */
val InsulinDialogUiState.insulinSelectionReady: Boolean
    get() = recordSource != RecordSource.PEN || penIcfg != null

val InsulinDialogUiState.confirmEnabled: Boolean
    get() = (insulin > 0.0 || eatingSoonTtChecked) && insulinSelectionReady
