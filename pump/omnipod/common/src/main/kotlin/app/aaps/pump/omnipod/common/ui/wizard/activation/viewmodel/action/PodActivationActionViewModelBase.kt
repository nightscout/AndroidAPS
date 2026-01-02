package app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase
import javax.inject.Provider

abstract class PodActivationActionViewModelBase(
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(pumpEnactResultProvider, logger, aapsSchedulers) {

    abstract fun isPodInAlarm(): Boolean

    abstract fun isPodActivationTimeExceeded(): Boolean

    abstract fun isPodDeactivatable(): Boolean
}