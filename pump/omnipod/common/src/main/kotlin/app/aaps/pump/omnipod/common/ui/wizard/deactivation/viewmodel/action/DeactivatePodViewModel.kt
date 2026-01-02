package app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase
import javax.inject.Provider

abstract class DeactivatePodViewModel(
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(pumpEnactResultProvider, logger, aapsSchedulers) {

    abstract fun discardPod()
}