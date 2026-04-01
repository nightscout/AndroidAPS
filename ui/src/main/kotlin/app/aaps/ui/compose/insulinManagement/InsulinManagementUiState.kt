package app.aaps.ui.compose.insulinManagement

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage

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

    // Activation dialog
    val activationMessage: String? = null,

    // Screen mode
    val screenMode: ScreenMode = ScreenMode.EDIT,

    // Loading/Error
    val isLoading: Boolean = true,
    val snackbarMessage: SnackbarMessage? = null
)
