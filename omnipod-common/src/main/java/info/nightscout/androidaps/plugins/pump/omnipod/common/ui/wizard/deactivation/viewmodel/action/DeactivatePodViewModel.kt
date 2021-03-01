package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action

import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class DeactivatePodViewModel : ActionViewModelBase() {

    abstract fun discardPod()
}