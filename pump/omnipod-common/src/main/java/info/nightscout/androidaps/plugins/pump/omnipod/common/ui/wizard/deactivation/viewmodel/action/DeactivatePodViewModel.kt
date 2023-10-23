package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class DeactivatePodViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(injector, logger, aapsSchedulers) {

    abstract fun discardPod()
}