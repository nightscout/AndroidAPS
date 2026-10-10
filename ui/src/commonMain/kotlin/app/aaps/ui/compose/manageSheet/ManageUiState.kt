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
    val showAuthorizedClients: Boolean = false,
    val showPairWithMaster: Boolean = false,
    // True on a master; on a client true only once paired. Gates EVERY Client-Control grid action — the mutating
    // editors (profile/TT/insulin/quick-wizard/scene/automation/food management), fill, temp basal/extended bolus,
    // AND the whole device + careportal sections (sensor/battery insert, BG check, note, exercise, …) now that
    // careportal rides Client-Control (Track B). Only the still-ungated SITE_ROTATION + config items stay regardless.
    val showMutatingActions: Boolean = true,
    // Pump management button — only on a master (!AAPSCLIENT); a client has no real pump (VirtualPump is hidden).
    val showPump: Boolean = true,
    val isPatchPump: Boolean = false,

    // Cancel button labels (with active values)
    val cancelTempBasalText: String = "",
    val cancelExtendedBolusText: String = "",

    // Pump
    val pumpPlugin: PluginBase,
    val customActions: List<CustomAction> = emptyList()
)
