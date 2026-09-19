package app.aaps.pump.omnipod.common.ui.wizard.compose

sealed class OmnipodWizardEvent {
    data object Finish : OmnipodWizardEvent()
}
