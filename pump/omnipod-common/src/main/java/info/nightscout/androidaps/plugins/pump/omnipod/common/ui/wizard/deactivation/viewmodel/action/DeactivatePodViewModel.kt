package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger

abstract class DeactivatePodViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : ActionViewModelBase(injector, logger, aapsSchedulers) {

    abstract fun discardPod()
}