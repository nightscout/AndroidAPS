package app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.rx.AapsSchedulers

abstract class InitializePodViewModel(
    instantiator: Instantiator,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : PodActivationActionViewModelBase(instantiator, logger, aapsSchedulers)