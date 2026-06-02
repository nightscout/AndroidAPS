package app.aaps.ui.compose.afrezzaDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg

@Immutable
data class AfrezzaDialogUiState(
    val selectedCartridge: Int? = null,       // 4, 8, or 12
    val afrezzaIcfg: ICfg? = null,           // Resolved Afrezza ICfg from InsulinManager
    val isConfigured: Boolean = false,        // Whether Afrezza insulin exists in InsulinManager
    val showConfirmation: Boolean = false,
    val isLogging: Boolean = false
)
