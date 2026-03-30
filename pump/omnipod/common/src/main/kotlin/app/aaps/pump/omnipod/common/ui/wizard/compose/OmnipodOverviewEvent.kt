package app.aaps.pump.omnipod.common.ui.wizard.compose

import android.content.Intent

/**
 * One-time events emitted by Omnipod overview ViewModels.
 */
sealed class OmnipodOverviewEvent {

    data class StartActivation(val activationType: ActivationType) : OmnipodOverviewEvent()
    data object StartDeactivation : OmnipodOverviewEvent()
    data object ShowHistory : OmnipodOverviewEvent()
    data class ShowDialog(val title: String, val message: String) : OmnipodOverviewEvent()
    data class ShowErrorDialog(val title: String, val message: String) : OmnipodOverviewEvent()
    data class StartActivity(val intent: Intent) : OmnipodOverviewEvent()
}
