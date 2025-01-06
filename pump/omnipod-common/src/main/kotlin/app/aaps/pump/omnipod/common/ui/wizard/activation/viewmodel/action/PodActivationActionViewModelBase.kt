package app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class PodActivationActionViewModelBase(
    instantiator: Instantiator,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(instantiator, logger, aapsSchedulers) {

    abstract fun isPodInAlarm(): Boolean

    abstract fun isPodActivationTimeExceeded(): Boolean

    abstract fun isPodDeactivatable(): Boolean
}