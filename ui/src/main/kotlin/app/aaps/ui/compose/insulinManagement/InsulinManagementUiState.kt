package app.aaps.ui.compose.insulinManagement

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.SnackbarMessage

@Immutable
data class InsulinManagementUiState(
    val insulins: List<ICfg> = emptyList(),
    val currentCardIndex: Int = 0,
    val activeInsulinLabel: String? = null,

    // Editor fields
    val editorName: String = "",
    val editorTemplate: InsulinType? = null,
    val editorConcentration: ConcentrationType = ConcentrationType.U100,
    val editorPeakMinutes: Int = 75,
    val editorDiaHours: Double = 5.0,

    // Screen mode
    val screenMode: ScreenMode = ScreenMode.EDIT,

    // Loading/Error
    val isLoading: Boolean = true,
    val snackbarMessage: SnackbarMessage? = null
)
