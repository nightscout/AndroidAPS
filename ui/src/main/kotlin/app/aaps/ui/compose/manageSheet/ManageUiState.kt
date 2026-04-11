package app.aaps.ui.compose.manageSheet

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.actions.CustomAction

/**
 * UI state for the Actions/Manage screen
 */
@Immutable
data class ManageUiState(
    // Visibility states
    val showTempTarget: Boolean = false,
    val showTempBasal: Boolean = false,
    val showCancelTempBasal: Boolean = false,
    val showExtendedBolus: Boolean = false,
    val showCancelExtendedBolus: Boolean = false,
    val showHistoryBrowser: Boolean = false,
    val showBatteryChange: Boolean = false,
    val showFill: Boolean = false,
    val isPatchPump: Boolean = false,

    // Cancel button labels (with active values)
    val cancelTempBasalText: String = "",
    val cancelExtendedBolusText: String = "",

    // Pump
    val pumpPlugin: PluginBase,
    val customActions: List<CustomAction> = emptyList()
)
