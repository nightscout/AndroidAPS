package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class PodActivationActionViewModelBase : ActionViewModelBase() {

    abstract fun isPodInAlarm(): Boolean

    abstract fun isPodActivationTimeExceeded(): Boolean

    abstract fun isPodDeactivatable(): Boolean
}