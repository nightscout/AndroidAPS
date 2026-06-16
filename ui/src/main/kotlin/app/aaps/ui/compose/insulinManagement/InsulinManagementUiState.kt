package app.aaps.ui.compose.insulinManagement

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.ui.compose.ScreenMode

sealed class PendingNavigation {
    data class CardSwitch(val targetIndex: Int) : PendingNavigation()
    data object Back : PendingNavigation()
}

@Immutable
data class InsulinManagementUiState(
    val insulins: List<ICfg> = emptyList(),
    val currentCardIndex: Int = 0,
    val activeInsulinLabel: String? = null,
    val activeConcentration: Double = 1.0,

    // Editor fields
    val editorNickname: String = "",
    val editorTemplate: InsulinType? = null,
    val editorConcentration: ConcentrationType = ConcentrationType.U100,
    val editorPeakMinutes: Int = 75,
    val editorDiaHours: Double = 5.0,
    val autoNameEnabled: Boolean = true,

    // Unsaved changes dialog
    val pendingNavigation: PendingNavigation? = null,

    // External (client→master sync) update arrived while the user has unsaved edits — master asks
    val externalUpdatePending: Boolean = false,

    // Screen mode
    val screenMode: ScreenMode = ScreenMode.EDIT,

    // Loading/Error
    val isLoading: Boolean = true
)
