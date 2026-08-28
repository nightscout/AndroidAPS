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
    // Concentration of the currently-running insulin. null = no/unknown active profile (not yet resolved,
    // or none) — deliberately NOT defaulted to 1.0, so it can never be mistaken for a real U100 active insulin.
    val activeConcentration: Double? = null,

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
