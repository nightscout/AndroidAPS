package app.aaps.ui.compose.afrezzaDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.ICfg

@Immutable
data class AfrezzaDialogUiState(
    val selectedCartridge: Int? = null,
    val afrezzaIcfg: ICfg? = null,
    val isConfigured: Boolean = false,
    val showConfirmation: Boolean = false,
    val isLogging: Boolean = false,
    val showMaxBasalPrompt: Boolean = false,
    val showDurationSelector: Boolean = false,
    val isApplyingBasal: Boolean = false,
    val showCarbPrompt: Boolean = false,
    val maxBasalRate: Double = 2.0,
    val maxBasalActive: Boolean = false,
    val maxBasalRemainingMinutes: Int = 0
)
