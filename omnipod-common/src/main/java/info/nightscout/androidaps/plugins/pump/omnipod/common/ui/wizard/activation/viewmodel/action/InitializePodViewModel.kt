package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.AAPSLogger

abstract class InitializePodViewModel(
    injector: HasAndroidInjector,
    logger: AAPSLogger
) : PodActivationActionViewModelBase(injector, logger)