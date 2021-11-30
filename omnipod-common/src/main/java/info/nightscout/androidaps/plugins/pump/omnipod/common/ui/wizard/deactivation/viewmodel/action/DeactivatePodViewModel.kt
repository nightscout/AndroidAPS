package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.common.viewmodel.ActionViewModelBase

abstract class DeactivatePodViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger
) : ActionViewModelBase(injector, logger) {

    abstract fun discardPod()
}